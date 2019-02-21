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

import org.apache.spark.sql.{ Row, SparkSession }
import org.apache.spark.mllib.evaluation.{ MulticlassMetrics, RegressionMetrics }
import spray.json._
import DefaultJsonProtocol._
import ch.chuv.lren.woken.messages.variables.{ VariableMetaData, VariableType }
import cats.data.NonEmptyList
import ch.chuv.lren.woken.messages.validation._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

/**
  * Holds the results of a model scoring
  */
trait ScoreHolder {
  def toScore: VariableScore
}

/** Computes the scores
  *
  * Created by Arnaud Jutzeler
  */
trait Scoring {

  def compute(algorithmOutput: NonEmptyList[JsValue], labels: NonEmptyList[JsValue]): Try[Score] = {
    val session = Scoring.spark.newSession()
    SparkSession.setActiveSession(session)

    val scores = Try(
      // Evaluation is lazy in Spark, perform it here
      compute(algorithmOutput, labels, session).toScore
    )

    SparkSession.clearActiveSession()

    scores
  }

  private[validation] def compute(algorithmOutput: NonEmptyList[JsValue],
                                  labels: NonEmptyList[JsValue],
                                  session: SparkSession): ScoreHolder
}

object Scoring {
  // Quick fix for spark 2.0.0
  val _ = System.setProperty("spark.sql.warehouse.dir", "/tmp ")

  private[validation] lazy val spark: SparkSession =
    SparkSession
      .builder()
      .master("local")
      .appName("Woken-validation")
      .getOrCreate()

  def enumerateLabel(targetMetaVariable: VariableMetaData): List[String] =
    targetMetaVariable.enumerations.fold(Nil: List[String])(_.map(_.code))

  def apply(targetMetaVariable: VariableMetaData): Scoring =
    targetMetaVariable.`type` match {
      case VariableType.binominal => BinaryClassificationScoring(enumerateLabel(targetMetaVariable))
      case VariableType.polynominal =>
        PolynomialClassificationScoring(enumerateLabel(targetMetaVariable))
      case _ => RegressionScoring
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
case class BinaryClassificationThresholdScoring() extends Scoring with LazyLogging {

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
            case row =>
              val error = s"Unexpected data row $row, expecting Row(Double, Double)"
              logger.warn(error)
              throw new IllegalArgumentException(error)
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
trait ClassificationScoreHolder extends ScoreHolder {

  def metrics: MulticlassMetrics
  def labelsMap: Map[String, Double]

  def matrix: Matrix = {

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

    Matrix(labels = labelsMap.keys.toList, values = array)
  }

}

case class PolynomialClassificationScoreHolder(override val metrics: MulticlassMetrics,
                                               override val labelsMap: Map[String, Double])
    extends ClassificationScoreHolder {

  override def toScore: VariableScore = PolynomialClassificationScore(
    `Confusion matrix` = matrix,
    `Accuracy` = metrics.accuracy,
    `Weighted recall` = metrics.weightedRecall,
    `Weighted precision` = metrics.weightedPrecision,
    `Weighted F1-score` = metrics.weightedFMeasure,
    `Weighted false positive rate` = metrics.weightedFalsePositiveRate
  )

  //TODO Add metrics by label?
  // Precision by label: metrics.precision(l)
  // Recall by label:  metrics.recall(l)
  // False positive: metrics.falsePositiveRate(l)
  // F-measure by label: metrics.fMeasure(l)

}

/**
  * Wrapper around Spark MLLib's MulticlassMetrics
  *
  * While waiting for usable BinaryClassificationThresholdScores...
  *
  */
case class BinaryClassificationScoreHolder(override val metrics: MulticlassMetrics,
                                           override val labelsMap: Map[String, Double])
    extends ClassificationScoreHolder {

  def recall: Double =
    metrics.confusionMatrix(0, 0) / (metrics.confusionMatrix(0, 0) + metrics.confusionMatrix(0, 1))

  def precision: Double =
    metrics.confusionMatrix(0, 0) / (metrics.confusionMatrix(0, 0) + metrics.confusionMatrix(1, 0))

  def f1Score: Double =
    2.0 * recall * precision / (recall + precision)

  def falsePositiveRate: Double =
    metrics.confusionMatrix(1, 0) / (metrics.confusionMatrix(1, 0) + metrics.confusionMatrix(1, 1))

  override def toScore: VariableScore = BinaryClassificationScore(
    `Confusion matrix` = matrix,
    `Accuracy` = metrics.accuracy,
    `Recall` = recall,
    `Precision` = precision,
    `F1-score` = f1Score,
    `False positive rate` = falsePositiveRate
  )
}

trait ClassificationScoring[S <: ClassificationScoreHolder] extends Scoring with LazyLogging {

  def enumeration: List[String]

  private[validation] def gen: (MulticlassMetrics, Map[String, Double]) => S

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def compute(algorithmOutput: NonEmptyList[JsValue],
                       label: NonEmptyList[JsValue],
                       session: SparkSession): S = {

    logger.whenDebugEnabled {
      logger.debug(s"Classification scoring with")
      logger.debug(s"* Algorithm output: ${algorithmOutput.toList.mkString(",")}")
      logger.debug(s"* Label: ${label.toList.mkString(",")}")
    }

    // Convert to dataframe
    val data: NonEmptyList[(String, String)] = algorithmOutput
      .zipWith(label)((_, _))
      .map({
        case (y, f) => (y.convertTo[String], f.convertTo[String])
      })

    val labelsMap = enumeration.zipWithIndex.map({ case (x, i) => (x, i.toDouble) }).toMap
    logger.whenDebugEnabled(
      logger.debug(s"* LabelsMap: $labelsMap")
    )

    val df = session
      .createDataFrame(
        data
          .map(x => {
            if (!labelsMap.contains(x._1)) {
              val error = s"Cannot find label ${x._1} in map $labelsMap"
              logger.warn(error)
              throw new Exception(error)
            }
            if (!labelsMap.contains(x._2)) {
              val error = s"Cannot find label ${x._2} in map $labelsMap"
              logger.warn(error)
              throw new Exception(error)
            }
            (labelsMap.get(x._1), labelsMap.get(x._2))
          })
          .toList
      )
      .toDF("output", "label")

    val predictionAndLabels =
      df.rdd.map {
        case Row(output_index: Double, label_index: Double) => (output_index, label_index)
        case row =>
          val error = s"Unexpected data row $row, expecting Row(Double, Double)"
          logger.warn(error)
          throw new IllegalArgumentException(error)
      }

    val metrics = new MulticlassMetrics(predictionAndLabels)

    gen(metrics, labelsMap)
  }

}

case class PolynomialClassificationScoring(override val enumeration: List[String])
    extends ClassificationScoring[PolynomialClassificationScoreHolder] {
  private[validation] def gen = PolynomialClassificationScoreHolder.apply
}

case class BinaryClassificationScoring(override val enumeration: List[String])
    extends ClassificationScoring[BinaryClassificationScoreHolder] {
  private[validation] def gen = BinaryClassificationScoreHolder.apply
}

/**
  *
  * Wrapper around Spark MLLib's RegressionMetrics
  *
  * TODO Add residual statistics
  *
  */
case class RegressionScoreHolder(metrics: RegressionMetrics) extends ScoreHolder {
  override def toScore: VariableScore = RegressionScore(
    `MSE` = metrics.meanSquaredError,
    `RMSE` = metrics.rootMeanSquaredError,
    `R-squared` = metrics.r2,
    `MAE` = metrics.meanAbsoluteError,
    `Explained variance` = metrics.explainedVariance
  )
}

object RegressionScoring extends Scoring with LazyLogging {

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def compute(algorithmOutput: NonEmptyList[JsValue],
                       label: NonEmptyList[JsValue],
                       session: SparkSession): RegressionScoreHolder = {

    def toDouble(v: JsValue): Double = v match {
      case JsString(s) => s.toDouble
      case n           => n.convertTo[Double]
    }

    // Convert to dataframe
    val data: NonEmptyList[(Double, Double)] = algorithmOutput
      .zipWith(label)((_, _))
      .map { case (y, f) => (toDouble(y), toDouble(f)) }
    val df = session.createDataFrame(data.toList).toDF("output", "label")

    val predictionAndLabels =
      df.rdd.map {
        case Row(prediction: Double, label: Double) => (prediction, label)
        case row =>
          val error = s"Unexpected data row $row, expecting Row(Double, Double)"
          logger.warn(error)
          throw new IllegalArgumentException(error)
      }

    RegressionScoreHolder(new RegressionMetrics(predictionAndLabels, false))
  }
}
