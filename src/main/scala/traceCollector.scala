package TraceCollector

import TraceType._
import SpanTypes._
import akka.actor.{ActorRef, Actor}
import scala.collection.mutable.Map
import scala.concurrent.duration._
import java.util.UUID


object TraceCollectorMessages {
	case class RecordReceived(msgId: UUID, sender: ActorRef, slf: ActorRef, traceMsg: TraceType, currTime: Long)
	case class RecordComplete(msgId: UUID, traceMsg: TraceType, finalTime: Long)
}


/**
*	TraceCollector is the local "Node" level collector that all sub-actors send spans to.
*	The aggregator param is the temporary location that complete spans are sent to. This should
* 	eventually be a service or external location that aggregates further in preperation for 
*	Zipkin or other.
**/
class TraceCollector(aggregator: ActorRef) extends Actor {

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
		//println("In Trace Collector: partial trace added.")
	}


	def recordComplete(msgId: UUID, traceMsg: TraceType, finalTime: Long) {
		spans(traceMsg.uuid)(msgId).timeToComplete = Some(finalTime)
		aggregator ! (traceMsg.uuid, spans(traceMsg.uuid)(msgId))
		//println("In Trace Collector: full trace complete.")
	}




}
