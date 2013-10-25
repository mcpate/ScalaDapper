package TraceCollector

import TraceType._
import SpanTypes._
import akka.actor.{ActorRef, Actor}
import scala.collection.mutable.Map
import java.util.UUID


object TraceCollectorMessages {
	case class RecordReceived(sender: ActorRef, slf: ActorRef, msg: TraceType, currTime: Long)
	case class RecordComplete(msg: TraceType, finalTime: Long)
}

class TraceCollector extends Actor {

	import TraceCollectorMessages._

	var pSpans = Map[UUID, PartialSpan]()
	
	def receive = {
		case RecordReceived(sender: ActorRef, slf: ActorRef, msg: TraceType, currTime: Long) => recordReceived(sender, slf, msg, currTime)
		case RecordComplete(msg: TraceType, finalTime: Long) => recordComplete(msg, finalTime)
	}

	def recordReceived(sender: ActorRef, slf: ActorRef, msg: TraceType, currTime: Long) {
		pSpans += (msg.uuid -> PartialSpan(sender, slf, msg.msg, System.nanoTime(), None))
		println("In Trace Collector: partial trace added.")
	}

	
	def recordComplete(msg: TraceType, finalTime: Long) {
		pSpans(msg.uuid).timeToComplete = Some(finalTime)
		println("In Trace Collector: full trace complete.")
	}
}






