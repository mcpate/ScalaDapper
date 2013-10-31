package SpiderPattern
import TraceType._
import TraceBuilder._
import TraceCollector.{TraceCollector, TraceCollectorMessages}
import akka.actor.{Actor, ActorRef, ActorPath, ActorContext, Props}
import scala.collection.mutable
import java.util.UUID


/**
*	We want to override Akka's default methods but since we don't have access
*	to all pieces of the classes (ActorRef, ActorContext) involved, we're instead
*	just isolating the parts we care about from the Akka API.
**/
trait Node { actor: Actor =>
	def send(actorRef: ActorRef, m: Any) { actorRef ! m }
	def reply(m: Any) { sender ! m }
	def forward(actorRef: ActorRef, m: Any) { actorRef.forward(m) }
	def actorOf(props: Props): ActorRef = actor.context.actorOf(props)
	def actorFor(actorPath: ActorPath): ActorRef = actor.context.actorFor(actorPath)
	def !(actorRef: ActorRef, m: Any) { actorRef ! m }
}

/**
*	This is the main trait for tracing. Data and Request are generic therefore any type of
*	Request for which some type of Data needs to be returned can be plugged in.
**/
trait WebNode extends Actor with Node {	

	import TraceCollectorMessages._

	// address of where to send traces to
	protected val collector: ActorRef

	// builds traces for messages that come in naked.
	protected val traceBuilder = TraceBuilder(10000)

	protected val selfRef = self

	// where we store the trace while passing the message to super class
	// this should get re-applied to the message once the super uses "!"
	protected var waitingTrace: Option[TraceType] = None


	// pathways coming into the node
	//protected val in = mutable.Set[ActorRef]()												

	// pathways going out of the node
	//protected val out = mutable.Set[ActorRef]()

	/**
	*	The following 4 methods override those defined in trait Node.
	*   They follow the pattern of: recording ActorRef, passing message on.
	**/
	override def send(actorRef: ActorRef, m: Any) {
	//	recordOutput(actorRef)
		actorRef ! (m, self)																
	}

	override def reply(m: Any) {
	//	recordOutput(sender)
		sender ! m
	}

	override def forward(actorRef: ActorRef, m: Any) {
	//	recordOutput(actorRef)
		actorRef forward m
	}

	override def actorOf(props: Props): ActorRef = {										
		val actorRef = context.actorOf(props)
	//	recordOutput(actorRef)
		actorRef
	}

	override def !(actorRef: ActorRef, m: Any) {
		waitingTrace match {
			case Some(traceMsg) => 	actorRef ! traceMsg
			case _ => actorRef ! m
		}
	}

	/**
	*	The following two methods are responsible for recording actors in and out.
	**/
	// def recordOutput(actorRef: ActorRef) {
	// 	out.add(actorRef)
	// }

	// def recordInput(actorRef: ActorRef) {
	// 	in.add(actorRef)
	// }


	/**
	*	The following are used for wrapping whatever "receive" is defined within the actor
	*	receive first tries to handleRequest (Spider action). If that doesn't catch wrappedReceive is called.
	**/
	abstract override def receive = wrappedReceive						
	
	
	def wrappedReceive: Receive = {
		case m: TraceType => handleTrace(sender, selfRef, m, now)
		case m: Any => 
			handleTrace(sender, selfRef, traceBuilder.buildTrace(m), now)
	}


	def synchronousDiagnostics(msg: Any): Long = {
		val start = now
		super.receive(msg)
		val end = now - start
		end
	}


	def handleTrace(sender: ActorRef, slf: ActorRef, traceMsg: TraceType, time: Long) = traceMsg.sampled match {
		case true =>
			val msgId = UUID.randomUUID()
			collector ! RecordReceived(msgId, sender, slf, traceMsg, time)
			waitingTrace = Some(traceMsg)
			val timing = synchronousDiagnostics(traceMsg.msg)
			collector ! RecordComplete(msgId, traceMsg, timing)
		case false =>
			waitingTrace = Some(traceMsg)
			super.receive(traceMsg.msg)
	}


	def now = System.nanoTime()



}