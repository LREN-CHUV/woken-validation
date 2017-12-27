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

import akka.actor.{ Actor, ActorLogging, Props }
import akka.event.LoggingReceive
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine
import eu.hbp.mip.woken.messages.validation._
import com.github.levkhomich.akka.tracing.ActorTracing

object ValidationActor {

  def props: Props =
    Props(new ValidationActor())

}

class ValidationActor extends Actor with ActorLogging with ActorTracing {

  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ValidationQuery(fold, model, data, varInfo) =>
      log.info("Received validation work!")
      // Reconstruct model using hadrian and validate over the provided data
      val replyTo = sender()
      try {

        val engine = PFAEngine.fromJson(model.compactPrint).head

        val inputData = engine.jsonInputIterator[AnyRef](data.iterator)
        val outputData: List[String] =
          inputData.map(x => { engine.jsonOutput(engine.action(x)) }).toList
        log.info("Validation work for " + fold + " done!")

        replyTo ! ValidationResult(fold, varInfo, outputData)
      } catch {
        // TODO Too generic!
        case e: Exception =>
          log.error(e, s"Error while validating model: $model")
          replyTo ! ValidationError(e.toString)
      }

    case e => log.error("Work not recognized by validation actor: " + e)
  }
}
