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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


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

// It seems in the example that HasId is mixed in so that the receive method can match this specific
// type of message 
case class SomeMessage(id: Long, text: String) extends HasId

/**
*	These are basic actors with no tracing
**/
class Forwarder(next: ActorRef) extends Actor with Node {
	var initialSender: ActorRef = null
	def receive = {
		case "Done Counting" => initialSender ! "Done Counting"
		case m: SomeMessage =>
			if (initialSender == null) { initialSender = sender }
			next ! m.copy()
		case m: Any => 
			if (initialSender == null) { initialSender = sender }
			next ! m
	}
}

//todo: figure out if m.copy() is the way to go here (vs. just "m")
class CountingForwarder(last: ActorRef) extends Actor with Node {
	var count = 0
	def receive = {
		case m: SomeMessage if (count >= 10000) =>
			last ! m.copy()
			sender ! "Done Counting"
		case m: SomeMessage =>
			count += 1
			sender ! m.copy()
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
		case m: String => println(m)
		case m: SomeMessage => println(m.text)
		case _ =>
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
	var basicTest = {
		var future = basicForwarderAlways ? "Some random text..."
		var result = Await.result(future, 3 seconds)
		future
	}

	var timeOne = new ListBuffer[Long]()
	for (i <- 1 to numTestRuns) {
		var (basicTestResult, basicTestTime) = helpers.time { basicTest }
		timeOne += basicTestTime
	}
	println("num results found: " + timeOne.length)
	println("average times for tests: " + ( (timeOne.reduceLeft(_+_)) / (timeOne.length) ) + "ns")






	/**
	*	TEST TWO
	**/
	println("\nRunning tracing tests on Node actors but with no Spider action")

	val tracingPrinter = system.actorOf(Props(new Printer with TimingDiagnostics), "tracingPrinter")
	val tracingForwarderCounting = system.actorOf(Props(new CountingForwarder(tracingPrinter) with TimingDiagnostics), "tracingForwarderCounting")
	val tracingForwarder = system.actorOf(Props(new Forwarder(tracingForwarderCounting) with TimingDiagnostics), "tracingForwarder")

	
	// Same as above test only this time on actors that mix in the logging trait.  Note that no spiders are being
	// sent.  This only tests the time for the messages to get through the system
	var tracingTest = {
		var future = tracingForwarder ? "Some random text..."
		var result = Await.result(future, 3 seconds)
		future
	}

	
	var timeTwo = new ListBuffer[Long]()
	for (i <- 1 to numTestRuns) {
		var  (tracingTestResult, tracingTestTime) = helpers.time { tracingTest }
		timeTwo += tracingTestTime
	}
	
	println("num results found: " + timeTwo.length)
	println("average times for tests: " + ( (timeTwo.reduceLeft(_+_)) / (timeTwo.length) ) + "ns")






	/**
	*	TEST TWO A
	**/
	println("\nRunning tracing tests on Node actors with diagnostic recording but still no spider")

	val tracingPrinterD = system.actorOf(Props(new Printer with TimingDiagnostics), "tracingPrinterD")
	val tracingForwarderCountingD = system.actorOf(Props(new CountingForwarder(tracingPrinterD) with TimingDiagnostics), "tracingForwarderCountingD")
	val tracingForwarderD = system.actorOf(Props(new Forwarder(tracingForwarderCountingD) with TimingDiagnostics), "tracingForwarderD")

	var tracingTestD = {
		var future = tracingForwarderD ? SomeMessage(1, "Some random message...")
		val result = Await.result(future, 3 seconds)
		future
	}

	var timeTwoA = new ListBuffer[Long]()
	for (i <- 1 to numTestRuns) {
		var (tracingTestResult, tracingTestTime) = helpers.time { tracingTestD }
		timeTwoA += tracingTestTime
	}

	println("num results found: " + timeTwoA.length)
	println("average times for tests: " + ( (timeTwoA.reduceLeft(_+_)) / (timeTwoA.length) ) + "ns")






	/**
	*	TEST THREE
	**/
	println("\nRunning tracing tests on Node actors with Spider action in middle of execution")

	// These are essestially the same actors as the previous test. New instances just for clarity.
	val tracingPrinterLive = system.actorOf(Props(new Printer with TimingDiagnostics), "tracingPrinterLive")
	val tracingForwarderCountingLive = system.actorOf(Props(new CountingForwarder(tracingPrinterLive) with TimingDiagnostics), "tracingForwarderCountingLive")
	val tracingForwarderLive = system.actorOf(Props(new Forwarder(tracingForwarderCountingLive) with TimingDiagnostics), "tracingForwarderLive")


	val returnAddress = system.actorOf(Props(new Actor {
		var results = ListBuffer[Any]()
		def receive = {
			case "size?" => sender ! results.size
			case m: Any =>
				println("got a result")
				results += m
		}
	}))

	tracingForwarderLive ! (Spider(returnAddress))


	// var tracingTestLive = {
	// 	var future = tracingForwarderLive ? (SomeMessage(2, "Some random text..."))
	// 	tracingForwarderLive ! (TimeDataRequest(2), Spider(returnAddress))
	// 	var result = Await.result(future, 3 seconds)
	// }

	// var timeThree = new ListBuffer[Long]()
	// for (i <- 1 to numTestRuns) {	
	// 	var (tracingTestLiveResult, tracingTestLiveTime) = helpers.time { tracingTestLive }
	// 	timeThree += tracingTestLiveTime
	// }

	// println("num results found: " + timeThree.length)
	// println("average times for tests: " + ( (timeThree.reduceLeft(_+_)) / (timeThree.length) ) + "ns")
	// val fu = returnAddress ? "size?"
 //    fu.onComplete {
	//  	case Success(value) => println("Got the callback with value: " + value)
	//   	case Failure(e) => e.printStackTrace
	// } 

	// Shut down 
	override protected def afterAll() {
		super.afterAll()
		system.shutdown()
	}
}