package SpanTypes

import java.util.UUID
import akka.actor.{ActorRef}
import scala.collection.mutable.ArrayBuffer


case class PartialSpan(origSender: ActorRef, slf: ActorRef, msg: Any, timeReceived: Long, var timeToComplete: Option[Long])
case class FullSpan(uuid: UUID,  spans: ArrayBuffer[PartialSpan])
