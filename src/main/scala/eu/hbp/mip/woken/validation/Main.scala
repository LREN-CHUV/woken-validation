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

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util.Try

object Main extends App {

  private val logger = LoggerFactory.getLogger("WokenValidation")

  val config                                              = ConfigFactory.load()
  private val clusterSystemName                           = config.getString("clustering.cluster.name")
  implicit val system: ActorSystem                        = ActorSystem(clusterSystemName, config)
  implicit val materializer: ActorMaterializer            = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  logger.info(s"Starting actor system $clusterSystemName")

  lazy val cluster = Cluster(system)

  // Start the local work dispatcher actor
  Cluster(system) registerOnMemberUp {
    system.actorOf(WorkDispatcherActor.props, name = "validation")
    system.actorOf(WorkDispatcherActor.props, name = "scoring")
  }

  Cluster(system).registerOnMemberRemoved {
    system.registerOnTermination(System.exit(0))
    system.terminate()

    // In case ActorSystem shutdown takes longer than 10 seconds,
    // exit the JVM forcefully anyway.
    // We must spawn a separate thread to not block current thread,
    // since that would have blocked the shutdown of the ActorSystem.
    new Thread {
      override def run(): Unit =
        if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
          System.exit(-1)
    }.start()
  }

  val routes: Route = pathPrefix("health") {
    get {
      complete("UP")
    }
  }

  // start a new HTTP server on port 8080 with our service actor as the handler
  val binding: Future[Http.ServerBinding] = Http().bindAndHandle(
    handler = routes,
    interface = config.getString("http.networkInterface"),
    port = config.getInt("http.port")
  )

}
