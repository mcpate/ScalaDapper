import SpiderDiagnostics._
import SpiderPattern._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
//import akka.dispatch.{Future, Promise, Await}
import scala.concurrent.{Future, Promise, Await}
import akka.testkit.TestKit
import org.scalatest.{WordSpecLike, BeforeAndAfterAll}			//$ Wordspec allows BDD style wording, BeforeAnd... allows setup and teardown tasks //$
import org.scalatest.matchers.ShouldMatchers					//$ MustMatchers provides wording
import akka.util.Timeout
import scala.concurrent.duration._
import akka.routing.BroadcastRouter
import scala.language.postfixOps								//$ Allows "1 seconds" vs "1.seconds"
import akka.pattern.ask
import scala.collection.mutable.ListBuffer

object helpers {

	def time[X](block: => X): (X, Long) = {
		val t0 = System.nanoTime()
		val result = block
		val t1 = System.nanoTime()
		val totalTime = t1 - t0
		//println("Elapsed time: " + totalTime + "ns")
		(result, totalTime)
	}
}



/**
*	These are the actors that have tracing built in
**/
class ForwarderWithTracing(next: ActorRef) extends Actor with Node {
	def receive = {
		case m: Any => next ! m
	}
}

class CountingForwarderWithTracing(last: ActorRef) extends Actor with Node {
	var count = 0
	def receive = {
		case m: Any if (count >= 10000) =>

	}
}

class PrinterWithTracing extends Actor with Node {
	def receive = {
		case m: Any => println(m)
	}
}


/**
*	These are basic actors with no tracing
**/
class Forwarder(next: ActorRef) extends Actor {
	var initialSender: ActorRef = null
	def receive = {
		case "Done Counting" => initialSender ! "Done Counting"
		case m: Any => 
			if (initialSender == null) { initialSender = sender }
			next ! m
	}
}

//todo: figure out if m.copy() is the way to go here (vs. just "m")
class CountingForwarder(last: ActorRef) extends Actor {
	var count = 0
	def receive = {
		case m: Any if (count >= 10000) => 
			last ! m
			sender ! "Done Counting"
		case m: Any => 
			count += 1
			sender ! m
	}
}

class Printer extends Actor {
	def receive = {
		case m: Any => println(m)
	}
}



/**
*	Actual Tests
**/
class TimingTest extends TestKit(ActorSystem("Spider")) with WordSpecLike with ShouldMatchers with BeforeAndAfterAll {

	implicit val timeout = Timeout(5 seconds)
	val numTestRuns = 100

	/**
	*	TEST ONE
	**/
	println("\nRunning basic test of messages through akka actors (stock actors)")

	// prints
	val basicPrinter = system.actorOf(Props[Printer], "basicPrinter")
	// forwards to next actor until count >= 100
	val basicForwarderCounting = system.actorOf(Props(new CountingForwarder(basicPrinter)), "countingForwarder")
	// always forwards to next actor
	val basicForwarderAlways = system.actorOf(Props(new Forwarder(basicForwarderCounting)), "basicForwarderAlways")

	// Fires messages back and forth 1000 times with basic actors
	lazy val basicTest = {
		new {
			val future = basicForwarderAlways ? "Some random text..."
			val result = Await.result(future, 3 seconds)
		}
	}

	var timesOne = new ListBuffer[Long]()
	for (i <- 1 to numTestRuns) {
		val (basicTestResult, basicTestTime) = helpers.time { basicTest }
		timesOne += basicTestTime
	}
	println("num results found: " + timesOne.length)
	println("average times for tests: " + ( (timesOne.reduceLeft(_+_)) / (timesOne.length - 1) ) + "ns")




	/**
	*	TEST TWO
	**/
	println("\nRunning tracing tests on Node actors but with no Spider action")

	val tracingPrinter = system.actorOf(Props(new Printer with Node), "tracingPrinter")
	val tracingForwarderCounting = system.actorOf(Props(new CountingForwarder(tracingPrinter) with Node), "tracingForwarderCounting")
	val tracingForwarder = system.actorOf(Props(new Forwarder(tracingForwarderCounting) with Node), "tracingForwarder")

	// Same as above test only this time on actors that mix in the logging trait.  Note that no spiders are being
	// sent.  This only tests the time for the messages to get through the system
	lazy val tracingTest = {
		new {
			val future = tracingForwarder ? "Some random text..."
			val result = Await.result(future, 3 seconds)
		}
	}

	var timesTwo = new ListBuffer[Long]()
	for (i <- 1 to numTestRuns) {
		val (tracingTestResult, tracingTestTime) = helpers.time { tracingTest }
		timesTwo += tracingTestTime
	}
	println("num results found: " + timesTwo.length)
	println("average times for tests: " + ( (timesTwo.reduceLeft(_+_)) / (timesTwo.length - 1) ) + "ns")




	/**
	*	TEST THREE
	**/
	println("\nRunning tracing tests on Node actors with Spider action in middle of execution")


	

	// Shut down 
	override protected def afterAll() {
		super.afterAll()
		system.shutdown()
	}
}