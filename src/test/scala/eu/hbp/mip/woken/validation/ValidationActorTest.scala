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

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import com.opendatagroup.hadrian.jvmcompiler.PFAEngine
import eu.hbp.mip.woken.messages.validation.{ ValidationQuery, ValidationResult }
import eu.hbp.mip.woken.meta.VariableMetaData
import eu.hbp.mip.woken.validation.util.JsonUtils
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.language.postfixOps

class ValidationActorTest
    extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with JsonUtils {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A simple regression model" should {
    "validate" in {

      val model = loadJson("/models/simple_regression.json").compactPrint

      val engine = PFAEngine.fromJson(model).head
      val data   = List("{\"v1\": 1, \"v2\": 2}", "{\"v1\": 2, \"v2\": 3}", "{\"v1\": 1, \"v2\": 5}")
      val labels = List("10.0", "20.0", "20.0")

      val validationRef = system.actorOf(Props[ValidationActor])

      validationRef ! ValidationQuery("Work",
                                      model,
                                      data,
                                      VariableMetaData("", "", "", None, None, None))
      val ValidationResult(_, _, result: List[String]) = receiveOne(60 seconds)

      result should contain theSameElementsInOrderAs labels

    }
  }
}
