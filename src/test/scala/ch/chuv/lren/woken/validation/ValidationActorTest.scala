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

import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit }
import ch.chuv.lren.woken.messages.validation.{ ValidationQuery, ValidationResult }
import ch.chuv.lren.woken.messages.variables.{ VariableMetaData, VariableType }
import ch.chuv.lren.woken.validation.util.JsonUtils
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._

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

      val model = loadJson("/models/simple_regression.json").asJsObject

      val data = List("{\"v1\": 1, \"v2\": 2}", "{\"v1\": 2, \"v2\": 3}", "{\"v1\": 1, \"v2\": 5}")
        .map(_.parseJson)
      val labels = List("10.0", "20.0", "20.0").map(JsString.apply)

      val validationRef = system.actorOf(Props[ValidationActor])

      validationRef ! ValidationQuery(
        0,
        model,
        data,
        VariableMetaData("",
                         "",
                         VariableType.text,
                         None,
                         None,
                         None,
                         None,
                         None,
                         None,
                         None,
                         None,
                         Set())
      )
      val ValidationResult(_, _, Right(result)) = receiveOne(60 seconds)

      result should contain theSameElementsInOrderAs labels

    }
  }
}
