package SpiderDiagnostics


import SpiderPattern._
import akka.actor.{ActorRef}
import java.util.UUID

/**
*	The below contains the various diagnostics to be used with the Package SpiderPattern.
*	SpiderPattern builds the framework, SpiderDiagnostics (this file) builds the diagnostics
*	to include.
**/

case class TracedMessage(id: UUID, originalMessage: Any)

// This is a wrapper for the data being collected and provides a common format for data being sent "home".
case class DiagnosticsData(uuid: UUID, overallTime: Long, timestamp: Long, nodeRef: WebNodeRef)


/**
*	This trait is a general diagnostic trait. It overrides methods in SpiderPattern concerned with
*	data collection.  It fires messages of type DiagnosticData which is a generic class meant to 
*	contain the data chunk you want sent back to "spider home".
**/ 
trait Diagnostics extends WebNode[(UUID, Long)] {

	// Timing metrics, UUID of Traced message matched to time it took for message to process
	private var map = Map[UUID, Long]()
	var timeBefore: Long = 0

	// Used for deciding when to create a "traced" message vs. leaving the message as is
	//val sampleRate = 10
	//var msgCount = 0

	override def sendSpiders(spiderHome: ActorRef, data: (UUID, Long), spi: Spider, collected: Set[ActorRef]) {
		spiderHome ! DiagnosticsData(data._1, data._2, now, selfNode)
		println("message sent home")
		super.sendSpiders(spiderHome, data, spi, collected)	
	}

	// Check if a traced message or if it should be.  If so take metric (starting time).
	override def before: Receive = {
		// This message is being traced.  Take initial timing metric.
		case TracedMessage => 
			timeBefore = now
			//msgCount += 1
		// Message isn't traced but should be, return traced message and record metric
		case m: Any => //if (msgCount > sampleRate && msgCount % sampleRate == 0) => 
			sender ! TracedMessage(UUID.randomUUID(), m)
			timeBefore = now
			//msgCount += 1
		case _ => //msgCount += 1
	}

	// If this is a traced message store (map) the message's UUID to "now - timeBefore"
	override def after: Receive = {
		case TracedMessage(id, message) => map = map + ( id -> ( now - timeBefore ))
		case _ =>
	} 
	
	def collection = {
		var toReturn: Option[(UUID, Long)] = None
		map.foreach { _ =>
			toReturn = toReturn ++ _
		}
	}

	def now = System.nanoTime()
}




/**
*	==============================================================================================================
*	The below are "concrete" diagnostics and are built upon the above trait and case class.
**/

// case class TimeDataRequest(id: Long)

// trait TimingDiagnostics extends Diagnostics[(Long, Long)] {

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