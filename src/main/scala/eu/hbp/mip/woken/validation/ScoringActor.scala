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

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{ Actor, ActorLogging, OneForOneStrategy, Props }
import akka.event.LoggingReceive
import akka.routing.{ OptimalSizeExploringResizer, RoundRobinPool }
import com.typesafe.config.Config
//import com.github.levkhomich.akka.tracing.ActorTracing
import ch.chuv.lren.woken.messages.validation.{ ScoringQuery, ScoringResult }

import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._
import scala.language.postfixOps

object ScoringActor {

  def props: Props =
    Props(new ScoringActor())

  def roundRobinPoolProps(config: Config): Props = {
    val scoringResizer = OptimalSizeExploringResizer(
      config
        .getConfig("scoring.resizer")
        .withFallback(
          config.getConfig("akka.actor.deployment.default.optimal-size-exploring-resizer")
        )
    )
    val scoringSupervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: Exception => Restart
      }

    RoundRobinPool(
      1,
      resizer = Some(scoringResizer),
      supervisorStrategy = scoringSupervisorStrategy
    ).props(ScoringActor.props)
  }

}

class ScoringActor extends Actor with ActorLogging /*with ActorTracing*/ {

  override def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ScoringQuery(algorithmOutput, groundTruth, targetMetaData) =>
      import ScoresProtocol._
      import spray.json._

      log.info("Received scoring work!")
      val replyTo = sender()

      val scores: Try[Scores] = Scoring(targetMetaData).compute(algorithmOutput, groundTruth)

      scores match {
        case Success(s) =>
          log.info("Scoring work complete")
          replyTo ! ScoringResult(s.toJson.asJsObject)
        case Failure(e) =>
          log.warning(e.toString)
          replyTo ! ScoringResult(s"""{"error": "$e"}""".parseJson.asJsObject)
      }

    case e => log.error(s"Work not recognized by scoring actor: $e")
  }

}
