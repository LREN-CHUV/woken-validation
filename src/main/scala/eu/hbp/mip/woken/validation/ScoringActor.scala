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
import com.github.levkhomich.akka.tracing.ActorTracing
import eu.hbp.mip.woken.messages.validation.{ ScoringQuery, ScoringResult }

object ScoringActor {

  def props: Props =
    Props(new ScoringActor)

}

class ScoringActor extends Actor with ActorLogging with ActorTracing {

  override def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case ScoringQuery(algorithmOutput, groundTruth, targetMetaData) =>
      import ScoresProtocol._
      import spray.json._

      log.info("Received scoring work!")
      val replyTo = sender()

      val scores: Scores = Scoring(targetMetaData).compute(algorithmOutput, groundTruth)
      replyTo ! ScoringResult(scores.toJson.asJsObject)

    case e => log.error("Work not recognized!: " + e)
  }

}
