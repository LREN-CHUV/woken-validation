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
    Props(new ScoringActor)

}

class WorkDispatcherActor extends Actor with ActorLogging with ActorTracing {

  import WorkDispatcherActor._

  var activeScoringActors: Set[ActorRef]       = Set.empty
  val activeScoringActorsLimit: Int            = Math.max(1, Runtime.getRuntime.availableProcessors())
  var pendingScoringQueries: Set[ScoringQuery] = Set.empty

  var activeValidationActors: Set[ActorRef]          = Set.empty
  val activeValidationActorsLimit: Int               = Math.max(1, Runtime.getRuntime.availableProcessors())
  var pendingValidationQueries: Set[ValidationQuery] = Set.empty

  var timerTask: Option[Cancellable] = None

  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case CheckPending =>
      if (pendingScoringQueries.nonEmpty) {
        if (activeScoringActors.size <= activeScoringActorsLimit) {
          val q = pendingScoringQueries.head
          dispatch(q)
          pendingScoringQueries -= q
        }
      } else if (pendingValidationQueries.nonEmpty) {
        if (activeValidationActors.size <= activeValidationActorsLimit) {
          val q = pendingValidationQueries.head
          dispatch(q)
          pendingValidationQueries -= q
        }
      } else {
        timerTask.forall(_.cancel())
        timerTask = None
      }

    case q: ScoringQuery =>
      if (activeScoringActors.size <= activeScoringActorsLimit) {
        dispatch(q)
      } else {
        startTimer()
        pendingScoringQueries += q
      }

    case q: ValidationQuery =>
      if (activeValidationActors.size <= activeValidationActorsLimit) {
        dispatch(q)
      } else {
        startTimer()
        pendingValidationQueries += q
      }

    case Terminated(a) =>
      log.debug(s"Actor terminated: $a")
      activeScoringActors -= a
      activeValidationActors -= a

    case _ => // ignore

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

  private def dispatch(q: ScoringQuery): Unit = {
    val scoringActorRef = context.actorOf(ScoringActor.props)
    scoringActorRef.tell(q, sender())
    context watch scoringActorRef
    activeScoringActors += scoringActorRef
  }

  private def dispatch(q: ValidationQuery): Unit = {
    val validationActorRef = context.actorOf(ValidationActor.props)
    validationActorRef.tell(q, sender())
    context watch validationActorRef
    activeScoringActors += validationActorRef
  }

}
