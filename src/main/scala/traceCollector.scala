package TraceCollector

import TraceType._
import SpanTypes._
import akka.actor.{ActorRef, Actor}
import scala.collection.mutable.Map
import java.util.UUID


object TraceCollectorMessages {
	case class RecordReceived(msgId: UUID, sender: ActorRef, slf: ActorRef, traceMsg: TraceType, currTime: Long)
	case class RecordComplete(msgId: UUID, traceMsg: TraceType, finalTime: Long)
}

class TraceCollector extends Actor {

	import TraceCollectorMessages._

	var spans = Map[UUID, Map[UUID, PartialSpan]]()
	
	def receive = {
		case RecordReceived(msgId: UUID, sender: ActorRef, slf: ActorRef, traceMsg: TraceType, currTime: Long) => 
			recordReceived(msgId, sender, slf, traceMsg, currTime)
		case RecordComplete(msgId: UUID, traceMsg: TraceType, finalTime: Long) => 
			recordComplete(msgId, traceMsg, finalTime)
	}

	def recordReceived(msgId: UUID, sender: ActorRef, slf: ActorRef, traceMsg: TraceType, currTime: Long) {
		if( spans.contains(traceMsg.uuid) ) {
			spans(traceMsg.uuid) += (msgId -> PartialSpan(sender, slf, traceMsg.msg, System.nanoTime(), None))
		} else {
			spans += (traceMsg.uuid -> Map(msgId -> PartialSpan(sender, slf, traceMsg.msg, System.nanoTime(), None)))
		}
		//pSpans += (msg.uuid -> PartialSpan(sender, slf, msg.msg, System.nanoTime(), None))
		println("In Trace Collector: partial trace added.")
	}


	def recordComplete(msgId: UUID, traceMsg: TraceType, finalTime: Long) {
		spans(traceMsg.uuid)(msgId).timeToComplete = Some(finalTime)
		println("In Trace Collector: full trace complete.")
	}
}
