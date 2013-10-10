package SpiderPattern

import akka.actor.{Actor, ActorRef, ActorPath, ActorContext, Props}
import scala.collection.mutable
import java.util.UUID


//case class Spider(home: ActorRef, trail: WebTrail = WebTrail())
//case class WebTrail(collected: Set[ActorRef] = Set(), uuid: UUID = UUID.randomUUID())
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
	def actorFor(actorPath: ActorPath): ActorRef = actor.context.actorFor(actorPath)
}

/**
*	This is the main trait for tracing. Data and Request are generic therefore any type of
*	Request for which some type of Data needs to be returned can be plugged in.
**/
trait WebNode[Data, TracedMessage, TopicOrActorRef] extends Actor with Node {										

	// pathways coming into the node
	protected val in = mutable.Set[ActorRef]()												
	// pathways going out of the node
	protected val out = mutable.Set[ActorRef]()

	// used to only handle a request once that travels through the web. This is set
	// once a spider has been here so that it isn't repeatedly analyzed.
	//protected var lastId: Option[UUID] = None

	def collection: Option[Data]													

	def selfNode = WebNodeRef(self, in.toList, out.toList)

	/**
	*	The following 4 methods override those defined in trait Node.
	*   They follow the pattern of: recording ActorRef, passing message on.
	**/
	override def send(actorRef: ActorRef, m: Any) {
		recordOutput(actorRef)
		actorRef ! (m, self)																
	}

	override def reply(m: Any) {
		recordOutput(sender)
		sender ! m
	}

	override def forward(actorRef: ActorRef, m: Any) {
		recordOutput(actorRef)
		actorRef forward m
	}

	override def actorOf(props: Props): ActorRef = {									
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
	
	def before: Receive
	def after: Receive
	
	def wrappedReceive: Receive = {
		// If it's a traced message then record metrics
		case m: TracedMessage => {
			recordInput(sender) //inbound actor ref recorded here
			before(m)
			super.receive(m) //if super sends (or anyone does) outbound actorref's are recorded in overridden methods above
			after(m)
			TopicOrActorRef ! collection
		}
		// If it's a normal message then just pass it up
		case m: Any => {
			//recordInput(sender)
			//before(m)
			super.receive(m)
			//after(m)
		}
	}

	// /**
	// *	Methods used when Spider specific action is envoked.
	// **/
	// def handleRequest: Receive = {
	// 	// The below case is: (Request, Spider) "if" lastId isn't set (ie it hasn't been visited before)
	// 	case m: RSWrap if !lastId.exists( _ == m.spi.trail.uuid ) => {
	// 		println("got spider request")
	// 		lastId = Some(m.spi.trail.uuid)
	// 		collect(m.req).map { data =>
	// 			sendSpiders(m.spi.home, data, (m.req, m.spi), m.spi.trail.collected) }
	// 		}
	// 	case (req: Any, spider @ Spider(ref, WebTrail(collected, uuid))) if !lastId.exists( _ == uuid ) => {
	// 		lastId = Some(uuid)
	// 		println("got a spider request")
	// 		// perform unique collection action, sendSpiders out after the other data
	// 		val reqCast = req.asInstanceOf[Request]
	// 		collect(reqCast).map { data => 
	// 			sendSpiders(ref, data, (reqCast, spider), collected) 
	// 		}

	// 	}
	// }

// 	def sendSpiders(ref: ActorRef, data: Data, msg: (Request, Spider), collected: Set[ActorRef]) {
// 		val (request, spider) = msg
// 		val newTrail = spider.trail.copy( collected = collected + self )											//$ update trail (get new trail) by adding self //$
// 		val newSpider = spider.copy( trail = newTrail )
// 		in.filterNot( in => collected.contains(in) ).foreach ( _ ! (request, newSpider) )							//$ filter on those that "don't" match
// 		out.filterNot( out => collected.contains(out) ).foreach ( _ ! (request, newSpider) )
// 	}

// }