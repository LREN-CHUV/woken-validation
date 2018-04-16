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

import java.io.{ File, PrintWriter }

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{ Actor, ActorLogging, OneForOneStrategy, Props }
import akka.event.LoggingReceive
import akka.routing.{ OptimalSizeExploringResizer, RoundRobinPool }
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine
import com.typesafe.config.Config
import ch.chuv.lren.woken.messages.validation._

import scala.io.Source

import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._
import scala.sys.process._

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

class ValidationActor
    extends Actor
    with ActorLogging
    with DefaultJsonProtocol /*with ActorTracing*/ {

  private val complexModels = Set("kNN", "naive_bayes", "neural_network", "linear_model")

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.TraversableOps"))
  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ValidationQuery(fold, model, data, varInfo) =>
      log.info("Received validation work!")
      // Reconstruct model using hadrian and validate over the provided data
      val replyTo = sender()
      Try {

        val modelNameO = model.fields.get("name").map(_.convertTo[String])
        modelNameO match {
          case Some(modelName) if complexModels.contains(modelName) =>
            log.info(s"Validing model $modelName using Titus")
            val modelFile = File.createTempFile(modelName, "-pfa")
            modelFile.deleteOnExit()
            val modelFileWriter = new PrintWriter(modelFile)
            modelFileWriter.write(model.compactPrint)
            modelFileWriter.close()

            val dataFile = File.createTempFile(modelName, "-data")
            dataFile.deleteOnExit()
            val dataFileWriter = new PrintWriter(dataFile)
            dataFileWriter.write(JsArray(data.toVector).compactPrint)
            dataFileWriter.close()

            val resultsFile = File.createTempFile(modelName, "-results")
            resultsFile.deleteOnExit()

            val processOutputFile = File.createTempFile(modelName, "-out")
            processOutputFile.deleteOnExit()

            val pfaEvalScript =
              if (new File("/pfa_eval.py").exists()) "/pfa_eval.py"
              else "src/main/python/pfa_eval.py"
            val cmd = Seq(pfaEvalScript,
                          modelFile.toPath.toString,
                          dataFile.toPath.toString,
                          resultsFile.toPath.toString)
            val process  = Process(cmd).run(new FileProcessLogger(processOutputFile))
            val exitCode = process.exitValue()

            if (exitCode == 0) {
              val results =
                Source.fromFile(resultsFile).getLines().mkString.parseJson.asInstanceOf[JsArray]
              replyTo ! ValidationResult(fold, varInfo, Right(results.elements.toList))
            } else {
              val msg = Source.fromFile(processOutputFile).getLines().mkString
              log.error(s"Error while validating model: $msg \nModel was: \n$model")
              replyTo ! ValidationResult(fold, varInfo, Left(msg))
            }

            modelFile.delete()
            dataFile.delete()
            resultsFile.delete()
            processOutputFile.delete()

          case _ =>
            val json   = model.compactPrint
            val engine = PFAEngine.fromJson(json).head

            val inputData = engine.jsonInputIterator[AnyRef](data.map(_.compactPrint).iterator)
            val outputData: List[JsValue] =
              inputData
                .map(x => {
                  engine.jsonOutput(engine.action(x)).toJson
                })
                .toList
            log.info(s"Validation work for $fold done!")

            replyTo ! ValidationResult(fold, varInfo, Right(outputData))
        }

      }.recover {
        case e: Exception =>
          log.error(e, s"Error $e while validating model: $model")
          replyTo ! ValidationResult(fold, varInfo, Left(e.toString))
      }

    case e => log.error(s"Work not recognized by validation actor: $e")
  }
}
