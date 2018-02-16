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
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine
import com.typesafe.config.Config
import ch.chuv.lren.woken.messages.validation._

//import com.github.levkhomich.akka.tracing.ActorTracing

import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps

object ValidationActor {

  def props: Props =
    Props(new ValidationActor())

  def roundRobinPoolProps(config: Config): Props = {

    val validationResizer = OptimalSizeExploringResizer(
      config
        .getConfig("validation.resizer")
        .withFallback(
          config.getConfig("akka.actor.deployment.default.optimal-size-exploring-resizer")
        )
    )
    val validationSupervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: Exception => Restart
      }

    RoundRobinPool(
      1,
      resizer = Some(validationResizer),
      supervisorStrategy = validationSupervisorStrategy
    ).props(ValidationActor.props)
  }

}

class ValidationActor extends Actor with ActorLogging /*with ActorTracing*/ {

  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ValidationQuery(fold, model, data, varInfo) =>
      log.info("Received validation work!")
      // Reconstruct model using hadrian and validate over the provided data
      val replyTo = sender()
      Try {

        val engine = PFAEngine.fromJson(model.compactPrint).head

        val inputData = engine.jsonInputIterator[AnyRef](data.iterator)
        val outputData: List[String] =
          inputData.map(x => { engine.jsonOutput(engine.action(x)) }).toList
        log.info("Validation work for " + fold + " done!")

        replyTo ! ValidationResult(fold, varInfo, outputData, None)

      }.recover {
        case e: Exception =>
          log.error(e, s"Error while validating model: $model")
          replyTo ! ValidationResult(fold, varInfo, Nil, Some(e.toString))
      }

    case e => log.error(s"Work not recognized by validation actor: $e")
  }
}
