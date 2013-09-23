/*import SpiderDiagnostics._
import SpiderPattern._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scala.concurrent.{Future, Promise, Await}
import akka.testkit.TestKit*/

/*trait WireTap extends Actor {
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
}*/


/*class SpiderTestSimple extends TestKit(ActorSystem("spiderSimple")) {

	val printer = system.actorOf(Props[Printer], "printer")
	def diagnostic = new Transformer(printer) with TimingDiagnostics
	val diagnosticActor = system.actorOf(Props(diagnostic), "diagnosticActor-1")

	printer ! SomeMessage(1, "some random message")

	system.shutdown()
}*/