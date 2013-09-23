
import SpiderDiagnostics._
import SpiderPattern._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
//import akka.dispatch.{Future, Promise, Await}
import scala.concurrent.{Future, Promise, Await}
import akka.testkit.TestKit
import org.scalatest.{WordSpecLike, BeforeAndAfterAll}			//$ Wordspec allows BDD style wording, BeforeAnd... allows setup and teardown tasks //$
import org.scalatest.matchers.MustMatchers					//$ MustMatchers provides wording
import akka.util.Timeout
import scala.concurrent.duration._
import akka.routing.BroadcastRouter
import scala.language.postfixOps								//$ Allows "1 seconds" vs "1.seconds"
		
trait WireTap extends Actor {
	def listener: ActorRef

	abstract override def receive = {
		case m =>
			super.receive(m)
			listener ! m
	}
}

case class SomeMessage(id: Long, text: String) extends HasId

class Transformer(next: ActorRef) extends Actor with Node {
	def receive = {
		case m: SomeMessage =>
			send( next, m.copy(text = (m.text.head.toUpper +: m.text.tail).toString ))
	}
}

class Printer extends Actor {
	def receive = {
		case m: SomeMessage => println( m.text )
	}
}

class SpiderTest extends TestKit(ActorSystem("spider")) with WordSpecLike with MustMatchers with BeforeAndAfterAll {
	
	implicit val timeout = Timeout(10 seconds)

	"The spider " must {
		"collect data about specific events " in {

			// create a printer actor that fires messages to the "testActor" in TestKit
			val printer = system.actorOf(Props(new Printer with TimingDiagnostics with WireTap {
				def listener = testActor															//$ testActor is part of TestKit //$
				}), "printer")

			// function to create new actors (Transformer actors that forward to Printer actors and use TimingDiagnostics)
			def createDiagnostic = new Transformer(printer) with TimingDiagnostics

			// create several actors
			val actor1 = system.actorOf(Props(createDiagnostic), "t-1")
			val actor2 = system.actorOf(Props(createDiagnostic), "t-2")
			val actor3 = system.actorOf(Props(createDiagnostic), "t-3")
			val routees = Vector[ActorRef](actor1, actor2, actor3)
			
			// put them in a specific type of router. BroadcastRouter "broadcasts" messages to all actors within.
			val router = system.actorOf(Props(createDiagnostic).withRouter(
				BroadcastRouter(routees = routees)), "router-to-transformers")
			val transformerWithRouter = system.actorOf(Props(new Transformer(router) with TimingDiagnostics), "transformer-with-router")
			val transformer = system.actorOf(Props(new Transformer(transformerWithRouter) with TimingDiagnostics), "first-transformer")

			//												actor1	--> print
			// transformer --> transformerWithRouter --> 	actor2	--> print
			//												actor3  --> print

			// send some text, starting the chain of events
			transformer ! SomeMessage(1, "some text to play with")
			expectMsg(SomeMessage(1, "some text to play with"))

			// create promise/future pair to be used below
			val p = Promise[Seq[DiagnosticsData[(Long, Long)]]]																				//$ These can be created and used in pairs. This is a hook into the actor eventually finishing.  This way we can hang on to results. //$
			val future = p.future

			// create a return address that the spiders will send diagnostic data to
			val returnAddress = system.actorOf(Props(new Actor {
				var results = List[DiagnosticsData[(Long, Long)]]()
				def receive = {
					case m: DiagnosticsData[(Long, Long)] =>
						results = results :+ m
						if (results.size == 6) p.success(results)								//$ Note that this is a cheat and that "in a real system you would work with what you have at a certain moment in time" //$
				}
			}))

			// this is the request for diagnostics data.  It could have been sent to any actor in the web that extends the diagnostics trait.
			//printer ! (TimeDataRequest(1), Spider(returnAddress))


			//val timingData = Await.result(future, 1 seconds)
			//timingData.map(_.data._1 must be (1))
			//println(timingData.mkString("\n"))

		}
	}

	override protected def afterAll() {
		super.afterAll()
		system.shutdown()
	}
}