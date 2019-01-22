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
import akka.actor.{ Actor, OneForOneStrategy, Props }
import akka.event.LoggingReceive
import akka.routing.{ OptimalSizeExploringResizer, RoundRobinPool }
import ch.chuv.lren.woken.errors.{ ErrorReporter, ValidationError }
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine
import com.typesafe.config.Config
import ch.chuv.lren.woken.messages.validation._
import com.typesafe.scalalogging.LazyLogging

import scala.io.Source
import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._

import scala.sys.process._

object ValidationActor extends LazyLogging {

  def props(pfaEvaluatorScript: String, errorReporter: ErrorReporter): Props =
    Props(new ValidationActor(pfaEvaluatorScript, errorReporter))

  def roundRobinPoolProps(config: Config, errorReporter: ErrorReporter): Props = {

    val validationResizer = OptimalSizeExploringResizer(
      config
        .getConfig("validation.resizer")
        .withFallback(
          config.getConfig("akka.actor.deployment.default.optimal-size-exploring-resizer")
        )
    )
    val validationSupervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case e: Exception =>
          logger.error("Error detected in Validation actor, restarting", e)
          Restart
      }

    val pfaEvaluatorScript = config.getString("validation.pfaEvaluatorScript")

    RoundRobinPool(
      1,
      resizer = Some(validationResizer),
      supervisorStrategy = validationSupervisorStrategy
    ).props(ValidationActor.props(pfaEvaluatorScript, errorReporter))
  }

}

class ValidationActor(val pfaEvaluatorScript: String, errorReporter: ErrorReporter)
    extends Actor
    with LazyLogging
    with DefaultJsonProtocol {

  private val complexModels =
    Set("kNN", "naive_bayes", "neural_network", "linear_model", "gradient_boosting")

  @SuppressWarnings(
    Array("org.wartremover.warts.Any",
          "org.wartremover.warts.NonUnitStatements",
          "org.wartremover.warts.AsInstanceOf",
          "org.wartremover.warts.TraversableOps")
  )
  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case query @ ValidationQuery(fold, model, data, varInfo) =>
      logger.info("Received validation work!")
      // Reconstruct model using hadrian and validate over the provided data
      val replyTo = sender()
      Try[Unit] {

        val modelNameO = model.fields.get("name").map(_.convertTo[String])
        modelNameO match {
          case Some(modelName) if complexModels.contains(modelName) =>
            logger.info(s"Validating model $modelName using Titus")

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

            val cmd = Seq(pfaEvaluatorScript,
                          modelFile.toPath.toString,
                          dataFile.toPath.toString,
                          resultsFile.toPath.toString)
            val processLogger = new FileProcessLogger(processOutputFile)
            val process       = Process(cmd).run(processLogger)
            val exitCode      = process.exitValue()

            processLogger.flush()

            if (exitCode == 0) {
              val results =
                Source.fromFile(resultsFile).getLines().mkString.parseJson.asInstanceOf[JsArray]
              val outputData: List[JsValue] = results.elements.toList

              logger.info(
                s"Validation work for fold $fold, variable ${varInfo.code} done. Results are $outputData"
              )
              replyTo ! ValidationResult(fold, varInfo, Right(outputData))
            } else {
              val msg = Source.fromFile(processOutputFile).getLines().mkString
              logger.error(
                s"Error while validating fold $fold, variable ${varInfo.code}: $msg \nModel was: \n$model"
              )
              val result = ValidationResult(fold, varInfo, Left(msg))
              errorReporter.report(
                new Exception(s"Error while validating fold $fold, variable ${varInfo.code}"),
                ValidationError(query, Some(result))
              )
              replyTo ! result
            }

            processLogger.close()
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

            logger.info(
              s"Validation work for fold $fold, variable ${varInfo.code} done. Results are $outputData"
            )
            replyTo ! ValidationResult(fold, varInfo, Right(outputData))
        }

      }.recover[Unit] {
        case e: Exception =>
          logger.error(
            s"Error while validating fold $fold, variable ${varInfo.code}: $e \nModel was: \n$model",
            e
          )
          val result = ValidationResult(fold, varInfo, Left(e.toString))
          errorReporter.report(e, ValidationError(query, Some(result)))
          replyTo ! result
      }

    case unhandled => logger.error(s"Work not recognized by validation actor: $unhandled")
  }
}
