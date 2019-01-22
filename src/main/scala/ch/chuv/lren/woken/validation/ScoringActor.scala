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
import akka.actor.{ Actor, OneForOneStrategy, Props }
import akka.event.LoggingReceive
import akka.routing.{ OptimalSizeExploringResizer, RoundRobinPool }
import ch.chuv.lren.woken.errors.{ ErrorReporter, ScoringError }
import com.typesafe.config.Config
import ch.chuv.lren.woken.messages.validation.{ Score, ScoringQuery, ScoringResult }
import com.typesafe.scalalogging.LazyLogging

import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._
import scala.language.postfixOps

object ScoringActor extends LazyLogging {

  def props(errorReporter: ErrorReporter): Props =
    Props(new ScoringActor(errorReporter))

  def roundRobinPoolProps(config: Config, errorReporter: ErrorReporter): Props = {
    val scoringResizer = OptimalSizeExploringResizer(
      config
        .getConfig("scoring.resizer")
        .withFallback(
          config.getConfig("akka.actor.deployment.default.optimal-size-exploring-resizer")
        )
    )
    val scoringSupervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case e: Exception =>
          logger.error("Error detected in Scoring actor, restarting", e)
          Restart
      }

    RoundRobinPool(
      1,
      resizer = Some(scoringResizer),
      supervisorStrategy = scoringSupervisorStrategy
    ).props(ScoringActor.props(errorReporter))
  }

}

class ScoringActor(errorReporter: ErrorReporter) extends Actor with LazyLogging {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case query @ ScoringQuery(algorithmOutput, groundTruth, targetMetaData) =>
      logger.info(
        s"Received scoring work for variable ${targetMetaData.label} of type ${targetMetaData.`type`}"
      )
      val replyTo = sender()

      val scores: Try[Score] = Scoring(targetMetaData).compute(algorithmOutput, groundTruth)

      scores match {
        case Success(score) =>
          logger.info("Scoring work complete")
          replyTo ! ScoringResult(Right(score))
        case Failure(e) =>
          logger.error(e.toString, e)
          val result = ScoringResult(Left(e.toString))
          errorReporter.report(e, ScoringError(query, Some(result)))
          replyTo ! result
      }

    case unhandled => logger.error(s"Work not recognized by scoring actor: $unhandled")
  }

}
