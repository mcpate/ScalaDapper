package SpiderPattern

import akka.actor.{Actor, ActorRef, ActorPath, ActorContext, Props}
import scala.collection.mutable
import java.util.UUID

case class Spider(home: ActorRef, trail: WebTrail = WebTrail())
case class WebTrail(collected: Set[ActorRef] = Set(), uuid: UUID = UUID.randomUUID())
case class WebNodeRef(node: ActorRef, in: List[ActorRef], out: List[ActorRef])


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
}

/**
*	This is the main trait for tracing. Data and Request are generic therefore any type of
*	Request for which some type of Data needs to be returned can be plugged in.
**/
trait WebNode[Data, Request] extends Actor with Node {										//$ [Data, Request] ensures the trait can only be mixed into a class or trait that also mixes in Data & Request (like parameters) //$

	// pathways coming into the node
	protected val in = mutable.Set[ActorRef]()												//$ Protected methods and variables are only accessible by classes or traits that explicitly mix them in //$

	// pathways going out of the node
	protected val out = mutable.Set[ActorRef]()

	// used to only handle a request once that travels through the web. This is set
	// once a spider has been here so that it isn't repeatedly analyzed.
	protected var lastId: Option[UUID] = None

	def collect(req: Request): Option[Data]													//$ This will return Some[Data] or None //$

	def selfNode = WebNodeRef(self, in.toList, out.toList)

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
	abstract override def receive = handleRequest orElse wrappedReceive						//$ orElse - used when reflecting over whether or not a partial function is defined over supplied argument //$
	
	// These are hooks for the diagnostic tasks that will be performed.
	def before: Receive
	def after: Receive
	
	// The below case class is used as a workaround to the Type Erasure experienced when trying to match in the "def wrappedReceive"
	// I was originally having issues with "...! m.isInstanceOf[(Request, Spider)]"  The case class seemed to solve this.
	// This was a recommended 'lightweight' solution.  An alternative was a more complicated "Manifest" class.
	case class EF(request: Request, spider: Spider)
	
	// Note that ALL actors coming in are recorded but before() and after() are only performed if the diagnostic
	// specifies it.  The TimingDiagnostics only call before and after if the message type extends "HasId"
	def wrappedReceive: Receive = {
		case m: Any if ! m.isInstanceOf[(Request, Spider)] => {
			recordInput(sender)
			before(m)
			super.receive(m)
			after(m)
		}
	}
	

	/**
	*	Methods used when Spider specific action is envoked.
	**/
	def handleRequest: Receive = {
		case (req: Request, spider @ Spider(ref, WebTrail(collected, uuid))) if !lastId.exists( _ == uuid ) => {          //$ lastId.exists returns true if Option is not empty and funct matches //$
			lastId = Some(uuid)
			// perform unique collection action, sendSpiders out after the other data. Note that I am having to cast back to Request
			// due to matching on RequestHolder above instead of Request.  Damn Erasure.
			collect(req).map { data => 
				sendSpiders(ref, data, (req, spider), collected) 
			}
		}
	}

	def sendSpiders(ref: ActorRef, data: Data, msg: (Request, Spider), collected: Set[ActorRef]) {
		val (request, spider) = msg
		val newTrail = spider.trail.copy( collected = collected + self )											//$ update trail (get new trail) by adding self //$
		val newSpider = spider.copy( trail = newTrail )
		in.filterNot( in => collected.contains(in) ).foreach ( _ ! (request, newSpider) )							//$ filter on those that "don't" match
		out.filterNot( out => collected.contains(out) ).foreach ( _ ! (request, newSpider) )
	}

}