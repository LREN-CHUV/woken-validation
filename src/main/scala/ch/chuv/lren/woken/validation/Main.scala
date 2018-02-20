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

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.{ HttpApp, Route }
import akka.stream.ActorMaterializer
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, ExecutionContextExecutor }
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object Main extends App {

  private val logger = LoggerFactory.getLogger("WokenValidation")

  val config: Config                                      = ConfigFactory.load()
  private val clusterSystemName                           = config.getString("clustering.cluster.name")
  implicit val system: ActorSystem                        = ActorSystem(clusterSystemName, config)
  implicit val materializer: ActorMaterializer            = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  logger.info(s"Step 1/3: Starting actor system $clusterSystemName...")

  lazy val cluster = Cluster(system)

  // Start the local work dispatcher actors
  Cluster(system) registerOnMemberUp {
    logger.info("Step 2/3: Cluster up, registering the actors...")

    system.actorOf(ValidationActor.roundRobinPoolProps(config), name = "validation")
    system.actorOf(ScoringActor.roundRobinPoolProps(config), name = "scoring")

    logger.info("Step 3/3: Startup complete.")
  }

  Cluster(system).registerOnMemberRemoved {
    logger.info("Exiting...")
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

  object HttpServer extends HttpApp {
    override def routes: Route = pathPrefix("health") {
      get {
        complete("UP")
      }
    }

  }

  // Start a new HTTP server on port 8081 with our service actor as the handler
  HttpServer.startServer(config.getString("http.networkInterface"),
                         config.getInt("http.port"),
                         system)

}
