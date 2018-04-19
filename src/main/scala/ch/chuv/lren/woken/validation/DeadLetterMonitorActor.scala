package ch.chuv.lren.woken.validation

import akka.actor.{Actor, DeadLetter, Props}
import com.typesafe.scalalogging.LazyLogging

object DeadLetterMonitorActor {
  def props: Props = Props(new DeadLetterMonitorActor())
}

class DeadLetterMonitorActor extends Actor with LazyLogging {

  def receive: PartialFunction[Any, Unit] = {
    case d: DeadLetter =>
      logger.error(s"Saw dead letter $d")

    case _ =>
      logger.debug("DeadLetterMonitorActor: got a message")

  }
}
