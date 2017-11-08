/*
 * Copyright 2017 LREN CHUV
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

import akka.actor.{
  Actor,
  ActorLogging,
  ActorSystem,
  ExtendedActorSystem,
  Extension,
  ExtensionKey,
  Props
}
import akka.cluster.Cluster
import com.opendatagroup.hadrian.datatype.{ AvroDouble, AvroString }
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine

import eu.hbp.mip.woken.messages.validation.{ ValidationError, ValidationQuery, ValidationResult }

// TODO This code will be common to all Akka service in containers -> put it as a small woken common lib!
class RemotePathExtensionImpl(system: ExtendedActorSystem) extends Extension {
  def getPath(actor: Actor) =
    actor.self.path.toStringWithAddress(system.provider.getDefaultAddress)
}
object RemotePathExtension extends ExtensionKey[RemotePathExtensionImpl]

class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
  def getAddress() =
    system.provider.getDefaultAddress
}
object RemoteAddressExtension extends ExtensionKey[RemoteAddressExtensionImpl]

class ValidationActor extends Actor with ActorLogging {

  def receive = {
    case ValidationQuery(fold, model, data, varInfo) ⇒
      log.info("Received validation work!")
      // Reconstruct model using hadrian and validate over the provided data
      val replyTo = sender()
      try {

        val engine = PFAEngine.fromJson(model).head

        val inputData = engine.jsonInputIterator[AnyRef](data.iterator)
        val outputData: List[String] =
          inputData.map(x => { engine.jsonOutput(engine.action(x)) }).toList
        log.info("Validation work for " + fold + " done!")

        replyTo ! ValidationResult(fold, varInfo, outputData)
      } catch {
        // TODO Too generic!
        case e: Exception => {
          import java.io.StringWriter
          import java.io.PrintWriter
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          log.error("Error while validating model: " + model)
          log.error(sw.toString)
          replyTo ! ValidationError(e.toString())
        }
      }
    case _ => log.error("Validation work not recognized!")
  }
}

object Main extends App {

  val system = ActorSystem("woken")

  // TODO Read the address from env vars
  //Cluster(system).join(Address("akka.tcp", "woken", "127.0.0.1", 8088))
  lazy val cluster = Cluster(system)

  // Start the local validation actor
  val validationActor = system.actorOf(Props[ValidationActor], name = "validation")
}
