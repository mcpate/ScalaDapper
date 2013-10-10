package SpiderDiagnostics


import SpiderPattern._
import akka.actor.{ActorRef}

/**
*	The below contains the various diagnostics to be used with the Package SpiderPattern.
*	SpiderPattern builds the framework, SpiderDiagnostics (this file) builds the diagnostics
*	to include.
**/

/**
*	This trait is a general diagnostic trait. It overrides methods in SpiderPattern concerned with
*	data collection.  It fires messages of type DiagnosticData which is a generic class meant to 
*	contain the data chunk you want sent back to "spider home".
**/ 
// trait Diagnostics[Data, TracedMessage, TopicOrActorRef] extends WebNode[Data, TracedMessage, Topi] {

// 	// override def sendSpiders(spiderHome: ActorRef, data: Data, msg: (Request, Spider), collected: Set[ActorRef]) {
// 	// 	spiderHome ! DiagnosticsData[Data](data, now, selfNode)
// 	// 	super.sendSpiders(spiderHome, data, msg, collected)	
// 	// }

// 	override def before = diagnoseBefore
// 	override def after = diagnoseAfter

// 	def diagnoseBefore: Receive
// 	def diagnoseAfter: Receive

// 	def now = System.nanoTime()
// }




/**
*	The below are "concrete" diagnostics and are built upon the above trait and case class.
**/
//case class TimeDataRequest(id: Long)

case class TracedMessage(id: Long, msg: String)

case class DiagnosticsData(data: (Long, Long), timestamp: Long, nodeRef: WebNodeRef)

trait TimingDiagnostics[TopicOrActorRef] extends WebNode[DiagnosticsData, TracedMessage, TopicOrActorRef] {

	var timeBefore: Long = 0
	var totalTime: Long = 0
	var msgId: Long = 0

	def now = System.nanoTime()

	override def before: Receive = {
		case _ => timeBefore = now
	}

	override def after: Receive = {
		case m: TracedMessage => 
			totalTime = ( now - timeBefore )	
			msgId = m.id
	}

	def collection = Some(DiagnosticsData(( msgId, totalTime ), now, selfNode))
}
