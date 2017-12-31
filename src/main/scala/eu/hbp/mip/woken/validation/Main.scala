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
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object Main extends App {

  val config = ConfigFactory.load()
  val system = ActorSystem(config.getString("clustering.cluster.name"), config)

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

}
