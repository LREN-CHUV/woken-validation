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

import org.scalactic.{Equality, TolerantNumerics}
import org.scalatest.{FlatSpec, Matchers}
import cats.data.NonEmptyList
import eu.hbp.mip.woken.validation.util.JsonUtils

class ScoresTest extends FlatSpec with Matchers with JsonUtils {

  "BinaryClassificationScores " should "be correct" in {

    import ScoresProtocol._
    import spray.json._
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.001)

    val scoring = BinaryClassificationScoring(List("a", "b"))

    val f = NonEmptyList.of(
      "\"a\"",
      "\"a\"",
      "\"b\"",
      "\"b\"",
      "\"b\"",
      "\"a\""
    )
    val y = NonEmptyList.of(
      "\"a\"",
      "\"b\"",
      "\"a\"",
      "\"b\"",
      "\"a\"",
      "\"a\""
    )

    val scores = scoring.compute(f, y)

    val json_object = scores.toJson

    json_object.asJsObject.fields("Confusion matrix").compactPrint should equal(
      "{\"labels\":[\"a\",\"b\"],\"values\":[[2.0,2.0],[1.0,1.0]]}"
    )
    json_object.asJsObject.fields("Accuracy").convertTo[Double] should equal(0.5)
    json_object.asJsObject.fields("Precision").convertTo[Double] should equal(2 / 3.0)
    json_object.asJsObject.fields("Recall").convertTo[Double] should equal(0.5)
    json_object.asJsObject.fields("F1-score").convertTo[Double] should equal(0.5714)
    json_object.asJsObject.fields("False positive rate").convertTo[Double] should equal(0.5)

    assertResult(loadJson("/binary_classification.json"))(json_object)
  }

  /*"BinaryClassificationThresholdScore " should "be correct" in {

    import org.scalactic.TolerantNumerics
    import spray.json._
    import core.validation.ScoresProtocol._
    implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.001)

    val scores = new BinaryClassificationThresholdScores()

    val f = List[String](
      "{\"a\": 0.8, \"b\": 0.2}",
      "{\"a\": 0.8, \"b\": 0.2}",
      "{\"a\": 0.3, \"b\": 0.7}",
      "{\"a\": 0.0, \"b\": 1.0}",
      "{\"a\": 0.4, \"b\": 0.6}",
      "{\"a\": 0.55, \"b\": 0.45}"
    )
    val y = List[String](
      "\"a\"",
      "\"b\"",
      "\"a\"",
      "\"b\"",
      "\"a\"",
      "\"a\""
    )

    scores.compute(f, y)

    val json_object = scores.toJson

    println(scores)

    json_object.asJsObject.fields.get("Accuracy").get.convertTo[Double] should equal (0.5)
    json_object.asJsObject.fields.get("Precision").get.convertTo[Double] should equal (2/3.0)
    json_object.asJsObject.fields.get("Recall").get.convertTo[Double] should equal (0.5)
    json_object.asJsObject.fields.get("F-measure").get.convertTo[Double] should equal (0.5714)

  }*/

  "ClassificationScore " should "be correct" in {

    import ScoresProtocol._
    import spray.json._
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.001)

    val scoring = PolynomialClassificationScoring(List("a", "b", "c"))

    val f = NonEmptyList.of(
      "\"a\"",
      "\"c\"",
      "\"b\"",
      "\"b\"",
      "\"c\"",
      "\"a\""
    )
    val y = NonEmptyList.of(
      "\"a\"",
      "\"c\"",
      "\"a\"",
      "\"b\"",
      "\"a\"",
      "\"b\""
    )

    val scores = scoring.compute(f, y)

    val json_object = scores.toJson

    json_object.asJsObject.fields("Confusion matrix").compactPrint should equal(
      "{\"labels\":[\"a\",\"b\",\"c\"],\"values\":[[1.0,1.0,1.0],[1.0,1.0,0.0],[0.0,0.0,1.0]]}"
    )
    json_object.asJsObject.fields("Accuracy").convertTo[Double] should equal(0.5)
    json_object.asJsObject
      .fields("Weighted Recall")
      .convertTo[Double] should equal(0.5) // a:1/3 (3), b: 1/2 (2), c:1/1 (1)
    json_object.asJsObject
      .fields("Weighted Precision")
      .convertTo[Double] should equal(0.5) // a:1/2 (3), b:1/2 (2), c:1/2 (1)
    json_object.asJsObject
      .fields("Weighted F1-score")
      .convertTo[Double] should equal(0.47777) // a:2/5 (3), b:1/2 (2), c:2/3 (1)
    json_object.asJsObject.fields("Weighted false positive rate").convertTo[Double] should equal(
      0.2833
    ) // a:1/3 (3), b:1/4 (2), c:1/5 (1)

    assertResult(loadJson("/classification_score.json"))(json_object)
  }

  "RegressionScores " should "be correct" in {

    import ScoresProtocol._
    import spray.json._
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.01)

    val scoring = RegressionScoring()

    val f = NonEmptyList.of("15.6", "0.0051", "23.5", "0.421", "1.2", "0.0325")
    val y = NonEmptyList.of("123.56", "0.67", "1078.42", "64.2", "1.76", "1.23")

    val scores = scoring.compute(f, y)

    val json_object = scores.toJson

    json_object.asJsObject.fields("R-squared").convertTo[Double] should equal(-0.2352)
    json_object.asJsObject.fields("RMSE").convertTo[Double] should equal(433.7)
    json_object.asJsObject.fields("MSE").convertTo[Double] should equal(188096.919)
    json_object.asJsObject.fields("MAE").convertTo[Double] should equal(204.8469)
    json_object.asJsObject
      .fields("Explained variance")
      .convertTo[Double] should equal(42048.9776) // E(y) = 211.64, SSreg = 252293.8657

    assertResult(loadJson("/regression_score.json"))(json_object)
  }

  "RegressionScores 2" should "be correct" in {

    import ScoresProtocol._
    import spray.json._
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.01)

    val scoring = RegressionScoring()

    val f = NonEmptyList.of("165.3", "1.65", "700.23", "66.7", "0.5", "2.3")
    val y = NonEmptyList.of("123.56", "0.67", "1078.42", "64.2", "1.76", "1.23")

    val scores = scoring.compute(f, y)

    val json_object = scores.toJson

    json_object.asJsObject.fields("R-squared").convertTo[Double] should equal(0.84153)
    json_object.asJsObject.fields("RMSE").convertTo[Double] should equal(155.34)
    json_object.asJsObject.fields("MSE").convertTo[Double] should equal(24129.974)
    json_object.asJsObject.fields("MAE").convertTo[Double] should equal(70.9566)
    json_object.asJsObject
      .fields("Explained variance")
      .convertTo[Double] should equal(65729.0537) // E(y) = 211.64, SSreg = 394374.3226

    assertResult(loadJson("/regression_score2.json"))(json_object)
  }
}
