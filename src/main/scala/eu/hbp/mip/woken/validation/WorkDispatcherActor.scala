/*
 * Copyright 2017 Human Brain Project MIP by LREN CHUV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hbp.mip.woken.validation

import akka.actor.{ Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated }
import akka.event.LoggingReceive
import com.github.levkhomich.akka.tracing.ActorTracing
import eu.hbp.mip.woken.messages.validation.{ ScoringQuery, ValidationQuery }

import scala.concurrent.duration._

object WorkDispatcherActor {

  private[validation] case object CheckPending

  def props: Props =
    Props(new WorkDispatcherActor())

}

class WorkDispatcherActor extends Actor with ActorLogging with ActorTracing {

  import WorkDispatcherActor._

  var activeScoringActors: Set[ActorRef]                   = Set.empty
  val activeScoringActorsLimit: Int                        = Math.max(1, Runtime.getRuntime.availableProcessors())
  var pendingScoringQueries: Set[(ScoringQuery, ActorRef)] = Set.empty

  var activeValidationActors: Set[ActorRef]                      = Set.empty
  val activeValidationActorsLimit: Int                           = Math.max(1, Runtime.getRuntime.availableProcessors())
  var pendingValidationQueries: Set[(ValidationQuery, ActorRef)] = Set.empty

  var timerTask: Option[Cancellable] = None

  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case CheckPending =>
      if (pendingScoringQueries.nonEmpty) {
        if (activeScoringActors.size <= activeScoringActorsLimit) {
          log.info("Dequeue scoring query")
          val head         = pendingScoringQueries.head
          val (q, replyTo) = head
          dispatchScoring(q, replyTo)
          pendingScoringQueries = pendingScoringQueries - head
        }
      } else if (pendingValidationQueries.nonEmpty) {
        if (activeValidationActors.size <= activeValidationActorsLimit) {
          log.info("Dequeue validation query")
          val head         = pendingValidationQueries.head
          val (q, replyTo) = head
          dispatchValidation(q, replyTo)
          pendingValidationQueries = pendingValidationQueries - head
        }
      } else {
        timerTask.forall(_.cancel())
        timerTask = None
      }

    case q: ScoringQuery =>
      val replyTo = sender()
      if (activeScoringActors.size <= activeScoringActorsLimit) {
        dispatchScoring(q, replyTo)
      } else {
        log.info("Queue scoring query")
        startTimer()
        pendingScoringQueries += ((q, replyTo))
      }

    case q: ValidationQuery =>
      val replyTo = sender()
      if (activeValidationActors.size <= activeValidationActorsLimit) {
        dispatchValidation(q, replyTo)
      } else {
        log.info("Queue validation query")
        startTimer()
        pendingValidationQueries += ((q, replyTo))
      }

    case Terminated(a) =>
      log.debug(s"Actor terminated: $a")
      activeScoringActors -= a
      activeValidationActors -= a

    case e => log.error("Work not recognized!: " + e)

  }

  private def startTimer(): Unit =
    if (timerTask.isEmpty) {
      // TODO: Akka 2.5
      //timers.startPeriodicTimer(CheckPending, Tick, 1.second)
      timerTask = Some(
        context.system.scheduler
          .schedule(1.second, 1.second, context.self, CheckPending)(context.dispatcher)
      )
    }

  private def dispatchScoring(q: ScoringQuery, replyTo: ActorRef): Unit = {
    log.info(s"Dispatch scoring query")
    val scoringActorRef = context.actorOf(ScoringActor.props)
    scoringActorRef.tell(q, replyTo)
    context watch scoringActorRef
    activeScoringActors += scoringActorRef
  }

  private def dispatchValidation(q: ValidationQuery, replyTo: ActorRef): Unit = {
    log.info(s"Dispatch validation query")
    val validationActorRef = context.actorOf(ValidationActor.props)
    validationActorRef.tell(q, replyTo)
    context watch validationActorRef
    activeValidationActors += validationActorRef
  }

}
