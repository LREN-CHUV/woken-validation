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
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.language.postfixOps

class ValidationActorTest
    extends TestKit(ActorSystem("MySpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A simple regression model" should {
    "validate" in {

      var model =
        """
     {
        |  "input": {
        |    "doc": "Input is the list of covariables and groups",
        |    "name": "DependentVariables",
        |    "type":"map",
        |    "values": "double"
        |  },
        |  "output": {
        |    "doc": "Output is the estimate of the variable",
        |    "type": "double"
        |  },
        |  "cells": {
        |    "model": {
        |      "type": {
        |        "name": "knn_model",
        |        "type":"record",
        |        "fields": [
        |          {
        |            "name": "k",
        |            "type": "int"
        |          },
        |          {
        |            "name": "samples",
        |            "type":{
        |              "type": "array",
        |              "items": {
        |                "type": "record",
        |                "name": "Sample",
        |                "fields": [
        |                  {
        |                    "name": "vars",
        |                    "type":{
        |                      "type": "array",
        |                      "items": "double"
        |                    }
        |                  },
        |                  {
        |                    "name": "label",
        |                    "type": "double"
        |                  }
        |                ]
        |              }
        |            }
        |          }
        |        ]
        |      },
        |      "init": {
        |        "k": 1,
        |        "samples": [{"vars":[1.0, 1.0], "label": 10.0},{"vars":[2.0, 2.0], "label": 20.0}]
        |      }
        |    }
        |  },
        |
        |  "fcns": {
        |    "toArray": {
        |      "params": [
        |        {
        |          "m": {
        |            "type": "map",
        |            "values": "double"
        |          }
        |        }
        |      ],
        |      "ret": {"type": "array", "items": "double"},
        |      "do": [
        |        {"let": {"input_map": "m"}},
        |        {
        |          "a.map": [
        |            {"type": {"type": "array", "items": "string"},
        |              "value": ["v1", "v2"]},
        |            {
        |              "params": [
        |                {
        |                  "key": {
        |                    "type": "string"
        |                  }
        |                }
        |              ],
        |              "ret": "double",
        |              "do": [
        |                {"attr": "input_map", "path": ["key"]}
        |              ]
        |            }
        |          ]
        |        }
        |      ]
        |    }
        |  },
        |
        |  "action": [
        |    {
        |      "let": {"model": {"cell": "model"}}
        |    },
        |    {
        |      "let": {
        |        "knn":
        |        {
        |          "model.neighbor.nearestK": [
        |            "model.k",
        |            {"u.toArray": ["input"]},
        |            "model.samples",
        |            {
        |              "params": [
        |                {
        |                  "x": {
        |                    "type": "array",
        |                    "items": "double"
        |                  }
        |                },
        |                {
        |                  "y": "Sample"
        |                }
        |              ],
        |              "ret": "double",
        |              "do": {
        |                "metric.simpleEuclidean": [
        |                  "x",
        |                  "y.vars"
        |                ]
        |              }
        |            }
        |          ]
        |        }
        |      }
        |    },
        |    {
        |      "let": {"label_list": {"type": {"type": "array", "items": "double"},
        |        "value": []}}
        |    },
        |    {
        |      "foreach": "neighbour",
        |      "in": "knn",
        |      "do": [
        |        {"set": {"label_list": {"a.append": ["label_list", "neighbour.label"]}}}
        |      ]
        |    },
        |    {
        |      "a.mean": ["label_list"]
        |    }
        |  ]
        |}
      """.stripMargin

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
