/*
 * Copyright (C) 2017  LREN CHUV for Human Brain Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.chuv.lren.woken.validation

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

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ScoringQuery(algorithmOutput, groundTruth, targetMetaData) =>
      log.info(s"Received scoring work for variable ${targetMetaData.label} of type ${targetMetaData.`type`}")
      val replyTo = sender()

      val scores: Try[ScoreHolder] = Scoring(targetMetaData).compute(algorithmOutput, groundTruth)

      scores match {
        case Success(s) =>
          log.info("Scoring work complete")
          replyTo ! ScoringResult(Right(s.toScore))
        case Failure(e) =>
          log.error(e, e.toString)
          replyTo ! ScoringResult(Left(e.toString))
      }

    case e => log.error(s"Work not recognized by scoring actor: $e")
  }

}
