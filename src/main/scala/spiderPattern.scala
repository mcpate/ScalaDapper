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
}

/**
*	This is the main trait for tracing. Data and Request are generic therefore any type of
*	Request for which some type of Data needs to be returned can be plugged in.
**/
trait WebNode extends Actor with Node {	

	import TraceCollectorMessages._

	protected val collector: ActorRef

	protected val traceBuilder = TraceBuilder(10000)

	protected val selfRef = self

	// pathways coming into the node
	protected val in = mutable.Set[ActorRef]()												//$ Protected methods and variables are only accessible by classes or traits that explicitly mix them in //$

	// pathways going out of the node
	protected val out = mutable.Set[ActorRef]()

	/**
	*	The following 4 methods override those defined in trait Node.
	*   They follow the pattern of: recording ActorRef, passing message on.
	**/
	override def send(actorRef: ActorRef, m: Any) {
		recordOutput(actorRef)
		actorRef ! (m, self)																//$ Note that " ! " and " tell " are the same thing //$
	}

	override def reply(m: Any) {
		recordOutput(sender)
		sender ! m
	}

	override def forward(actorRef: ActorRef, m: Any) {
		recordOutput(actorRef)
		actorRef forward m
	}

	override def actorOf(props: Props): ActorRef = {										//$ Used for tracking creation of child actors //$
		val actorRef = context.actorOf(props)
		recordOutput(actorRef)
		actorRef
	}

	/**
	*	The following two methods are responsible for recording actors in and out.
	**/
	def recordOutput(actorRef: ActorRef) {
		out.add(actorRef)
	}

	def recordInput(actorRef: ActorRef) {
		in.add(actorRef)
	}


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


	def synchronousDiagnostics(msg: TraceType): Long = {
		val start = now
		super.receive(msg.msg)
		val end = now - start
		end
	}


	def handleTrace(sender: ActorRef, slf: ActorRef, traceMsg: TraceType, time: Long) = traceMsg.sampled match {
		case true =>
			val msgId = UUID.randomUUID()
			collector ! RecordReceived(msgId, sender, slf, traceMsg, time)
			val timing = synchronousDiagnostics(traceMsg)
			collector ! RecordComplete(msgId, traceMsg, timing)

		case false =>
			super.receive(traceMsg.msg)
	}


	def now = System.nanoTime()



}