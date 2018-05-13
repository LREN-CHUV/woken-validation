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

import java.io.File

import akka.actor.{ActorSystem, DeadLetter}
import akka.cluster.Cluster
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.collection.JavaConversions._
import scala.language.postfixOps
import scala.util.Try

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object Main extends App {

  private val logger = LoggerFactory.getLogger("WokenValidation")

  // Order of loading:
  // 1. Akka configuration hard-coded for clustering (present here to avoid clashes with tests no using a cluster)
  // 2. Configuration of Akka pre-defined in akka.conf, configurable by environment variables
  // 3. Configuration of Kamon pre-defined in kamon.conf, configurable by environment variables
  // 4. Custom configuration defined in application.conf and backed by reference.conf from the libraries
  //    for algorithms can be overriden by environment variables
  lazy val config: Config = {
    val remotingConfig = ConfigFactory.parseResourcesAnySyntax("akka-remoting.conf").resolve()
    val remotingImpl = remotingConfig.getString("remoting.implementation")
    ConfigFactory
      .parseString(
        """
          |akka {
          |  actor.provider = cluster
          |  extensions += "akka.cluster.pubsub.DistributedPubSub"
          |}
        """.stripMargin)
      .withFallback(ConfigFactory.parseResourcesAnySyntax("akka.conf"))
      .withFallback(ConfigFactory.parseResourcesAnySyntax(s"akka-$remotingImpl-remoting.conf"))
      .withFallback(ConfigFactory.parseResourcesAnySyntax("kamon.conf"))
      .withFallback(ConfigFactory.load())
      .resolve()
  }

  val kamonConfig = config.getConfig("kamon")

  if (kamonConfig.getBoolean("enabled") || kamonConfig.getBoolean("prometheus.enabled") || kamonConfig
    .getBoolean("zipkin.enabled")) {

    logger.info("Kamon configuration:")
    logger.info(config.getConfig("kamon").toString)
    logger.info(s"Start monitoring...")

    Kamon.reconfigure(config)

    val hostSystemMetrics = kamonConfig.getBoolean("system-metrics.host.enabled")
    if (hostSystemMetrics) {
      logger.info(s"Start Sigar metrics...")
      Try {
        val sigarLoader = new SigarLoader(classOf[Sigar])
        sigarLoader.load()
      }

      Try(
        SigarProvisioner.provision(
          new File(System.getProperty("user.home") + File.separator + ".native")
        )
      ).recover { case e: Exception => logger.warn("Cannot provision Sigar", e) }

      if (SigarProvisioner.isNativeLoaded)
        logger.info("Sigar metrics are available")
      else
        logger.warn("Sigar metrics are not available")
    }

    if (hostSystemMetrics || kamonConfig.getBoolean("system-metrics.jvm.enabled")) {
      logger.info(s"Start collection of system metrics...")
      SystemMetrics.startCollecting()
    }

    if (kamonConfig.getBoolean("prometheus.enabled"))
      Kamon.addReporter(new PrometheusReporter)

    if (kamonConfig.getBoolean("zipkin.enabled"))
      Kamon.addReporter(new ZipkinReporter)
  }

  private val clusterSystemName = config.getString("clustering.cluster.name")
  private val seedNodes         = config.getStringList("akka.cluster.seed-nodes").toList

  implicit val system: ActorSystem                        = ActorSystem(clusterSystemName, config)
  implicit val materializer: ActorMaterializer            = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  logger.info(s"Step 1/3: Starting actor system $clusterSystemName...")
  logger.info(s"Actor system should connect to cluster nodes ${seedNodes.mkString(",")}")

  lazy val cluster = Cluster(system)

  // Start the local work dispatcher actors
  cluster registerOnMemberUp {
    logger.info("Step 2/3: Cluster up, registering the actors...")

    val validation = system.actorOf(ValidationActor.roundRobinPoolProps(config), name = "validation")
    val scoring = system.actorOf(ScoringActor.roundRobinPoolProps(config), name = "scoring")

    val mediator = DistributedPubSub(system).mediator

    mediator ! DistributedPubSubMediator.Put(validation)
    mediator ! DistributedPubSubMediator.Put(scoring)

    val deadLetterMonitorActor =
      system.actorOf(DeadLetterMonitorActor.props, name = "deadLetterMonitor")

    // Subscribe to system wide event bus 'DeadLetter'
    system.eventStream.subscribe(deadLetterMonitorActor, classOf[DeadLetter])

    logger.info("Step 3/3: Startup complete.")
  }

  cluster.registerOnMemberRemoved {
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
    val healthRoute: Route = pathPrefix("health") {
      get {
        // TODO: proper health check is required, check db connection, check cluster availability...
        if (cluster.state.leader.isEmpty)
          complete((StatusCodes.InternalServerError, "No leader elected for the cluster"))
        else if (cluster.state.members.size < 2)
          complete((StatusCodes.InternalServerError, "Expected at least Woken + Woken validation server in the cluster"))
        else
          complete("UP")
      }
    }

    val readinessRoute: Route = pathPrefix("readiness") {
      get {
        if (cluster.state.leader.isEmpty)
          complete((StatusCodes.InternalServerError, "No leader elected for the cluster"))
        else
          complete("READY")
      }
    }

    override def routes: Route = cors()(healthRoute ~ readinessRoute)

  }

  // Start a new HTTP server on port 8081 with our service actor as the handler
  HttpServer.startServer(config.getString("http.networkInterface"),
                         config.getInt("http.port"),
                         system)

}
