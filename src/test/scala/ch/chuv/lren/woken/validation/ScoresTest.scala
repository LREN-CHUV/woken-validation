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

import org.scalactic.{ Equality, TolerantNumerics }
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import org.apache.spark.sql.SparkSession
import cats.data.NonEmptyList
import ch.chuv.lren.woken.messages.validation._
import ch.chuv.lren.woken.validation.util.JsonUtils
import spray.json._
import ch.chuv.lren.woken.messages.validation.validationProtocol._

class ScoresTest extends FlatSpec with Matchers with BeforeAndAfterAll with JsonUtils {

  "BinaryClassificationScores " should "be correct" in {

    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.001)

    val scoring = BinaryClassificationScoring(List("a", "b"))

    val f = NonEmptyList.of("a", "a", "b", "b", "b", "a").map(JsString.apply)
    val y = NonEmptyList.of("a", "b", "a", "b", "a", "a").map(JsString.apply)

    val scores = scoring.compute(f, y)

    scores.isSuccess shouldBe true

    val expected = BinaryClassificationScore(
      `Confusion matrix` = Matrix(List("a", "b"), Array(Array(2.0, 2.0), Array(1.0, 1.0))),
      `Accuracy` = 0.5,
      `Precision` = 2 / 3.0,
      `Recall` = 0.5,
      `F1-score` = 0.5714285714285715,
      `False positive rate` = 0.5
    )

    val score = scores.get.asInstanceOf[BinaryClassificationScore]

    score.`Confusion matrix` shouldBe expected.`Confusion matrix`
    score.`Accuracy` shouldEqual expected.`Accuracy`
    score.`Precision` shouldEqual expected.`Precision`
    score.`Recall` shouldEqual expected.`Recall`
    score.`F1-score` shouldEqual expected.`F1-score`
    score.`False positive rate` shouldEqual expected.`False positive rate`

    assertResult(loadJson("/binary_classification.json"))(score.asInstanceOf[VariableScore].toJson)

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

  "PolynomialClassificationScore " should "be correct" in {

    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.001)

    val scoring = PolynomialClassificationScoring(List("a", "b", "c"))

    val f = NonEmptyList.of("a", "c", "b", "b", "c", "a").map(JsString.apply)
    val y = NonEmptyList.of("a", "c", "a", "b", "a", "b").map(JsString.apply)

    val scores = scoring.compute(f, y)

    scores.isSuccess shouldBe true

    // `Weighted precision a:1/2 (3), b: 1/2 (2), c:1/2 (1)
    // `Weighted recall    a:1/3 (3), b: 1/2 (2), c:1/1 (1)
    // `Weigthed F1-score  a:2/5 (3), b: 1/2 (2), c:2/3 (1)
    // `Weigthed false positive rate  a:1/3 (3), b: 1/4 (2), c:1/5 (1)

    val expected = PolynomialClassificationScore(
      `Confusion matrix` =
        Matrix(List("a", "b", "c"),
               Array(Array(1.0, 1.0, 1.0), Array(1.0, 1.0, 0.0), Array(0.0, 0.0, 1.0))),
      `Accuracy` = 0.5,
      `Weighted precision` = 0.5,
      `Weighted recall` = 0.5,
      `Weighted F1-score` = 0.47777777777777775,
      `Weighted false positive rate` = 0.2833333333333333
    )

    val score = scores.get

    score shouldBe expected

    assertResult(loadJson("/classification_score.json"))(score.toJson)
  }

  "RegressionScores " should "be correct" in {

    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.01)

    val scoring = RegressionScoring

    val f = NonEmptyList.of(15.6, 0.0051, 23.5, 0.421, 1.2, 0.0325).map(JsNumber.apply)
    val y = NonEmptyList.of(123.56, 0.67, 1078.42, 64.2, 1.76, 1.23).map(JsNumber.apply)

    val scores = scoring.compute(f, y)

    scores.isSuccess shouldBe true

    val expected = RegressionScore(
      `MSE` = 188096.91975654333,
      `MAE` = 204.8469,
      `R-squared` = -0.2352658303269044,
      `RMSE` = 433.7014177479056,
      `Explained variance` = 42048.97761921002 // E(y) = 211.64, SSreg = 252293.8657
    )

    val score = scores.get

    score shouldBe expected

    assertResult(loadJson("/regression_score.json"))(score.toJson)
  }

  "RegressionScores 2" should "be correct" in {

    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.01)

    val scoring = RegressionScoring

    val f = NonEmptyList.of(165.3, 1.65, 700.23, 66.7, 0.5, 2.3).map(JsNumber.apply)
    val y = NonEmptyList.of(123.56, 0.67, 1078.42, 64.2, 1.76, 1.23).map(JsNumber.apply)

    val scores = scoring.compute(f, y)

    scores.isSuccess shouldBe true

    val expected = RegressionScore(
      `MSE` = 24129.97443333334,
      `MAE` = 70.95666666666668,
      `R-squared` = 0.8415341785355228,
      `RMSE` = 155.3382581122028,
      `Explained variance` = 65729.05376666668 // E(y) = 211.64, SSreg = 394374.3226
    )

    val score = scores.get

    score shouldBe expected

    assertResult(loadJson("/regression_score2.json"))(score.toJson)
  }

  "No match between classes in data and labels" should "be reported as error" in {
    // Regression test for bug https://trello.com/c/WSwZA70e/97-validation-match-error

    val scoring = PolynomialClassificationScoring(List("AD", "MCI", "CN", "WRONG_CLASS"))

    val f = NonEmptyList
      .of(
        "MCI",
        "MCI",
        "Other",
        "AD",
        "MCI"
      )
      .map(JsString.apply)
    val y = NonEmptyList
      .of(
        "CN",
        "CN",
        "CN",
        "AD",
        "MCI"
      )
      .map(JsString.apply)

    f.length shouldBe y.length
    val scores = scoring.compute(f, y)

    scores.isSuccess shouldBe false

    scores.failed.get.getMessage shouldBe "Cannot find label Other in map Map(AD -> 0.0, MCI -> 1.0, CN -> 2.0, WRONG_CLASS -> 3.0)"
  }

  override def afterAll() {
    try super.afterAll()
    finally Scoring.spark.stop()
  }
}
