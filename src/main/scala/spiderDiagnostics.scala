package SpiderDiagnostics


import SpiderPattern._
import akka.actor.{ActorRef}
import java.util.UUID


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
// trait Diagnostics[Data, Request] extends WebNode[Data, Request] {

// 	override def sendSpiders(spiderHome: ActorRef, data: Data, msg: (Request, Spider), collected: Set[ActorRef]) {
// 		spiderHome ! DiagnosticsData[Data](data, now, selfNode)
// 		super.sendSpiders(spiderHome, data, msg, collected)	
// 	}

// 	override def before = diagnoseBefore
// 	override def after = diagnoseAfter

// 	def diagnoseBefore: Receive
// 	def diagnoseAfter: Receive

// 	//def now = System.nanoTime()
// }




// /**
// *	This is an actual diagnostic data message including the timestamp of when the diagnostic was taken.
// **/
// case class DiagnosticsData[Data](data: Data, timestamp: Long, nodeRef: WebNodeRef)

// /**
// *	The below are "concrete" diagnostics and are built upon the above trait and case class.
// **/
// case class TraceType(uuid: UUID, data: Any)

// case class TimeDataRequest(id: Long)

// trait TimingDiagnostics extends Diagnostics[(Long, Long), TimeDataRequest] {

// 	private var map = Map[Long, Long]()
// 	var timeBefore: Long = 0

// 	def diagnoseBefore: Receive = {
// 		case m: HasId => timeBefore = now
// 		case _ =>
// 	}

// 	def diagnoseAfter: Receive = {
// 		case m: HasId => map = map + ( m.id -> ( now - timeBefore ))
// 		case _ =>
// 	}

// 	def collect(req: TimeDataRequest) = map.get( req.id ).map( ( req.id, _ ))
// }

// trait HasId {
// 	def id: Long
// }