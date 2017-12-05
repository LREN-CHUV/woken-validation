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

import org.apache.spark.sql.{ Row, SparkSession }
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.evaluation.RegressionMetrics
import spray.json.{ JsNumber, JsObject, JsString, JsValue, JsonFormat }
import spray.json._
import DefaultJsonProtocol._
import eu.hbp.mip.woken.meta.VariableMetaData
import cats.data.NonEmptyList

import scala.util.Try

/** Results of a model scoring
  */
trait Scores

/** Computes the scores
  *
  * Created by Arnaud Jutzeler
  */
trait Scoring {
  // Quick fix for spark 2.0.0
  val _ = System.setProperty("spark.sql.warehouse.dir", "/tmp ")

  def compute(algorithmOutput: NonEmptyList[String], labels: NonEmptyList[String]): Try[Scores] = {
    val session = Scoring.spark.newSession()
    SparkSession.setActiveSession(session)

    val scores = Try(
      compute(algorithmOutput, labels, session)
    )

    SparkSession.clearActiveSession()

    scores
  }

  private[validation] def compute(algorithmOutput: NonEmptyList[String],
                                  labels: NonEmptyList[String],
                                  session: SparkSession): Scores
}

object Scoring {

  private[validation] lazy val spark: SparkSession =
    SparkSession
      .builder()
      .master("local")
      .appName("Woken-validation")
      .getOrCreate()

  def enumerateLabel(targetMetaVariable: VariableMetaData): List[String] =
    targetMetaVariable.enumerations.fold(Nil: List[String])(_.keys.toList)

  def apply(targetMetaVariable: VariableMetaData): Scoring =
    targetMetaVariable.`type` match {
      case "binominal"   => BinaryClassificationScoring(enumerateLabel(targetMetaVariable))
      case "polynominal" => PolynomialClassificationScoring(enumerateLabel(targetMetaVariable))
      case _             => RegressionScoring()
    }

  type LabelScores = NonEmptyList[(String, Double)]
}

/*
import Scoring.LabelScores

/**
 * Wrapper around Spark MLLib's BinaryClassificationMetrics
 *
 * Metrics for binary classifiers whose output is the score (probability) of the one of the values (positive)
 *
 * TODO To be tested
 * TODO Problem: BinaryClassificationMetrics does not provide confusion matrices...
 * It should ultimately replace BinaryClassificationScoring
 */
@SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
case class BinaryClassificationThresholdScores(metrics: NonEmptyList[BinaryClassificationMetrics],
                                               labelScores: LabelScores)
    extends Scores {

  /** Get the index for T = 0.5 */
  def t_0_5: Double =
    metrics.head
      .thresholds()
      .max()(new Ordering[Double]() {
        override def compare(x: Double, y: Double): Int =
          if (x < 0.5) {
            if (y < 0.5) Ordering[Double].compare(x, y)
            else -1
          } else if (x >= 0.5) {
            if (y > x || y < 0.5) 1 else -1
          } else 0
      })

}

@SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
case class BinaryClassificationThresholdScoring() extends Scoring {

  override def compute(algorithmOutput: NonEmptyList[String],
                       label: NonEmptyList[String]): BinaryClassificationThresholdScores = {

    val data: NonEmptyList[(LabelScores, String)] = algorithmOutput
      .zipWith(label)((_, _))
      .map({
        case (f, y) => {
          val as = f.parseJson.convertTo[Map[String, Double]]
          (as.toList.toNel, y.parseJson.convertTo[String])
        }
      })

    // TODO To be changed once we have the schema
    val labels = NonEmptyList.of(data.head._1.keys.head -> 0.0, data.head._1.keys.last -> 1.0)

    // Convert to dataframe
    val metrics = labels.map(label => {
      new BinaryClassificationMetrics(
        spark
          .createDataFrame(
            data
              .map({
                case (x, y) =>
                  val key = label._1
                  (x.get(key), if (y == key) 1.0 else 0.0)
              })
              .toList
          )
          .toDF("output", "label")
          .rdd
          .map {
            case Row(output: Double, label: Double) => (output, label)
          }
      )
    })

    BinaryClassificationThresholdScores(metrics, labels)
  }
}

 */

/**
  * Wrapper around Spark MLLib's MulticlassMetrics
  *
  */
trait ClassificationScores extends Scores {

  def metrics: MulticlassMetrics
  def labelsMap: Map[String, Double]

  def matrixJson(): JsValue = {

    val matrix = metrics.confusionMatrix
    val labels = metrics.labels

    val n = labelsMap.size
    val m = labels.length

    val array = Array.ofDim[Double](n, n)

    // Build the complete matrix
    for (i <- 0 until m) {
      for (j <- 0 until m) {
        array(labels(i).toInt)(labels(j).toInt) = matrix(i, j)
      }
    }

    JsObject(
      "labels" -> labelsMap.keys.toList.toJson,
      "values" -> array.toJson
    )
  }

}

case class PolynomialClassificationScores(override val metrics: MulticlassMetrics,
                                          override val labelsMap: Map[String, Double])
    extends ClassificationScores

/**
  * Wrapper around Spark MLLib's MulticlassMetrics
  *
  * While waiting for usable BinaryClassificationThresholdScores...
  *
  */
case class BinaryClassificationScores(override val metrics: MulticlassMetrics,
                                      override val labelsMap: Map[String, Double])
    extends ClassificationScores {

  def recall: Double =
    metrics.confusionMatrix(0, 0) / (metrics.confusionMatrix(0, 0) + metrics.confusionMatrix(0, 1))

  def precision: Double =
    metrics.confusionMatrix(0, 0) / (metrics.confusionMatrix(0, 0) + metrics.confusionMatrix(1, 0))

  def f1score: Double =
    2.0 * recall * precision / (recall + precision)

  def falsePositiveRate: Double =
    metrics.confusionMatrix(1, 0) / (metrics.confusionMatrix(1, 0) + metrics.confusionMatrix(1, 1))

}

trait ClassificationScoring[S <: ClassificationScores] extends Scoring {

  def enumeration: List[String]

  private[validation] def gen: (MulticlassMetrics, Map[String, Double]) => S

  override def compute(algorithmOutput: NonEmptyList[String],
                       label: NonEmptyList[String],
                       session: SparkSession): S = {

    // Convert to dataframe
    val data: NonEmptyList[(String, String)] = algorithmOutput
      .zipWith(label)((_, _))
      .map({
        case (y, f) => (y.parseJson.convertTo[String], f.parseJson.convertTo[String])
      })

    val labelsMap = enumeration.zipWithIndex.map({ case (x, i) => (x, i.toDouble) }).toMap

    val df = session
      .createDataFrame(data.map(x => { (labelsMap.get(x._1), labelsMap.get(x._2)) }).toList)
      .toDF("output", "label")

    val predictionAndLabels =
      df.rdd.map {
        case Row(output_index: Double, label_index: Double) => (output_index, label_index)
      }

    val metrics = new MulticlassMetrics(predictionAndLabels)

    gen(metrics, labelsMap)
  }

}

case class PolynomialClassificationScoring(override val enumeration: List[String])
    extends ClassificationScoring[PolynomialClassificationScores] {
  private[validation] def gen = PolynomialClassificationScores.apply
}

case class BinaryClassificationScoring(override val enumeration: List[String])
    extends ClassificationScoring[BinaryClassificationScores] {
  private[validation] def gen = BinaryClassificationScores.apply
}

/**
  *
  * Wrapper around Spark MLLib's RegressionMetrics
  *
  * TODO Add residual statistics
  *
  */
case class RegressionScores(metrics: RegressionMetrics) extends Scores

case class RegressionScoring(`type`: String = "regression") extends Scoring {

  override def compute(algorithmOutput: NonEmptyList[String],
                       label: NonEmptyList[String],
                       session: SparkSession): RegressionScores = {

    // Convert to dataframe
    val data: NonEmptyList[(Double, Double)] = algorithmOutput
      .zipWith(label)((_, _))
      .map {
        case (y: String, f: String) =>
          (y.parseJson.convertTo[Double], f.parseJson.convertTo[Double])
      }
    val df = session.createDataFrame(data.toList).toDF("output", "label")

    val predictionAndLabels =
      df.rdd.map {
        case Row(prediction: Double, label: Double) => (prediction, label)
      }

    RegressionScores(new RegressionMetrics(predictionAndLabels, false))
  }
}

@SuppressWarnings(Array("org.wartremover.warts.Product", "org.wartremover.warts.Serializable"))
object ScoresProtocol extends DefaultJsonProtocol {

  /*
  implicit object BinaryClassificationThresholdScoresJsonFormat
      extends RootJsonFormat[BinaryClassificationThresholdScores] {

    def write(s: BinaryClassificationThresholdScores): JsObject = {

      def getClosest(num: Double, listNums: List[Double]) = listNums match {
        case Nil  => Double.MaxValue
        case list => list.minBy(v => math.abs(v - num))
      }

      // Get the index for T = 0.5
      val t_0_5 = s.t_0_5

      JsObject(
        // Accuracy for T = 0.5
        "Accuracy" -> JsNumber(0.5), // TODO

        // Precision for T = 0.5
        "Precision" -> JsNumber(
          s.metrics.head
            .precisionByThreshold()
            .filter({ case (x: Double, y: Double) => x == t_0_5 })
            .first()
            ._2
        ),
        // Recall for T = 0.5
        "Recall" -> JsNumber(
          s.metrics.head
            .recallByThreshold()
            .filter({ case (x: Double, y: Double) => x == t_0_5 })
            .first()
            ._2
        ),
        // F-Measure for T = 0.5
        "F1-score" -> JsNumber(
          s.metrics.head
            .fMeasureByThreshold()
            .filter({ case (x: Double, y: Double) => x == t_0_5 })
            .first()
            ._2
        ),
        // Area Under ROC Curve
        "Area Under ROC Curve" -> JsNumber(s.metrics.head.areaUnderPR),
        // Area Under Precision-Recall Curve
        "Area Under Precision-Recall Curve" -> JsNumber(s.metrics.head.areaUnderROC)
      )

      //TODO Add metrics by threshold...
      // Thresholds: precision.map(_._1)
      // Precision by threshold: metrics.precisionByThreshold
      // Recall by threshold: metrics.recallByThreshold
      // F1-score by threshold: metrics.fMeasureByThreshold
      // Fbeta-score by threshold: metrics.fMeasureByThreshold(beta)
      // Precision-Recall Curve: metrics.pr
      // ROC Curve: metrics.roc)
    }

    def read(value: JsValue): Nothing = value match {
      case _ => deserializationError("To be implemented")
    }
  }
   */

  implicit object BinaryClassificationScoresJsonFormat
      extends RootJsonFormat[BinaryClassificationScores] {

    def write(s: BinaryClassificationScores): JsObject =
      JsObject(
        "Confusion matrix"    -> s.matrixJson,
        "Accuracy"            -> JsNumber(s.metrics.accuracy),
        "Recall"              -> JsNumber(s.recall),
        "Precision"           -> JsNumber(s.precision),
        "F1-score"            -> JsNumber(s.f1score),
        "False positive rate" -> JsNumber(s.falsePositiveRate)
      )

    def read(value: JsValue): Nothing = value match {
      case _ => deserializationError("To be implemented")
    }
  }

  implicit object PolynomialClassificationScoresJsonFormat
      extends RootJsonFormat[PolynomialClassificationScores] {

    def write(s: PolynomialClassificationScores): JsObject =
      JsObject(
        "Confusion matrix"             -> s.matrixJson,
        "Accuracy"                     -> JsNumber(s.metrics.accuracy),
        "Weighted Recall"              -> JsNumber(s.metrics.weightedRecall),
        "Weighted Precision"           -> JsNumber(s.metrics.weightedPrecision),
        "Weighted F1-score"            -> JsNumber(s.metrics.weightedFMeasure),
        "Weighted false positive rate" -> JsNumber(s.metrics.weightedFalsePositiveRate)
      )

    //TODO Add metrics by label?
    // Precision by label: metrics.precision(l)
    // Recall by label:  metrics.recall(l)
    // False positive: metrics.falsePositiveRate(l)
    // F-measure by label: metrics.fMeasure(l)

    def read(value: JsValue): Nothing = value match {
      case _ => deserializationError("To be implemented")
    }
  }

  implicit object RegressionScoresJsonFormat extends RootJsonFormat[RegressionScores] {

    def write(s: RegressionScores): JsObject =
      JsObject(
        "MSE"                -> JsNumber(s.metrics.meanSquaredError),
        "RMSE"               -> JsNumber(s.metrics.rootMeanSquaredError),
        "R-squared"          -> JsNumber(s.metrics.r2),
        "MAE"                -> JsNumber(s.metrics.meanAbsoluteError),
        "Explained variance" -> JsNumber(s.metrics.explainedVariance)
      )

    def read(value: JsValue): Nothing = value match {
      case _ => deserializationError("To be implemented")
    }
  }

  implicit object ScoresJsonFormat extends JsonFormat[Scores] {
    def write(s: Scores): JsObject =
      JsObject((s match {
        case b: BinaryClassificationScores     => b.toJson
        case c: PolynomialClassificationScores => c.toJson
        case r: RegressionScores               => r.toJson
      }).asJsObject.fields + ("type" -> JsString(s.getClass.getSimpleName)))

    def read(value: JsValue): Scores =
      // If you need to read, you will need something in the
      // JSON that will tell you which subclass to use
      value.asJsObject.fields("type") match {
        case JsString("BinaryClassificationScores") => value.convertTo[BinaryClassificationScores]
        case JsString("PolynomialClassificationScores") =>
          value.convertTo[PolynomialClassificationScores]
        case JsString("RegressionScores") => value.convertTo[RegressionScores]
        case _                            => value.convertTo[RegressionScores]
      }
  }
}
