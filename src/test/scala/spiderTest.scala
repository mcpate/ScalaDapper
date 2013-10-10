
// import SpiderDiagnostics._
// import SpiderPattern._
// import akka.actor.{Actor, ActorRef, ActorSystem, Props}
// //import akka.dispatch.{Future, Promise, Await}
// import scala.concurrent.{Future, Promise, Await}
// import akka.testkit.TestKit
// import org.scalatest.{WordSpecLike, BeforeAndAfterAll}			//$ Wordspec allows BDD style wording, BeforeAnd... allows setup and teardown tasks //$
// import org.scalatest.matchers.MustMatchers					//$ MustMatchers provides wording
// import akka.util.Timeout
// import scala.concurrent.duration._
// import akka.routing.BroadcastRouter
// import scala.language.postfixOps								//$ Allows "1 seconds" vs "1.seconds"
// import scala.collection.mutable.ListBuffer
// import akka.pattern.ask


		
// object helpers {

// 	def time[X](block: => X): (X, Long) = {
// 		val t0 = System.nanoTime()
// 		val result = block
// 		val t1 = System.nanoTime()
// 		val totalTime = t1 - t0
// 		//println("Elapsed time: " + totalTime + "ns")
// 		(result, totalTime)
// 	}
// }

// trait WireTap extends Actor {
// 	def listener: ActorRef

// 	abstract override def receive = {
// 		case m =>
// 			super.receive(m)
// 			listener ! m
// 	}
// }

// case class TraceMessage(id: Long, text: String) extends HasId

// class Forwarder(next: ActorRef) extends Actor with Node {
// 	var initialSender: ActorRef = null
// 	def receive = {
// 		case m: TraceMessage =>
// 			if (initialSender == null) { initialSender = sender }
// 			next ! m.copy()
// 		case "Done Counting" => initialSender ! "Done Counting"
// 		case m: Any =>
// 			if (initialSender == null) { initialSender = sender }
// 			next ! m
// 	}
// }


// class CountingForwarder(last: ActorRef) extends Actor with Node {
// 	var count = 0
// 	def receive = {
// 		case m: Any if (count >= 10000) =>
// 			last ! m
// 			sender ! "Done Counting"
// 		case m: Any =>
// 			count += 1
// 			sender ! m
// 	}
// }

// class Printer extends Actor {
// 	def receive = {
// 		case m: TraceMessage => println( m.text )
// 		case m: String => println(m)
// 		case _ => println("In Printer - misc. message received")
// 	}
// }

// class SpiderTest extends TestKit(ActorSystem("spider")) with WordSpecLike with MustMatchers with BeforeAndAfterAll {
	
// 	implicit val timeout = Timeout(10 seconds)
// 	val numTestRuns = 1000


// 	/**
// 	*	Test of the Spider Pattern.  Testing of plain actors below
// 	**/

// 	"The spider " must {
// 		"collect data about specific events " in {

// 			// create a printer actor that fires messages to the "testActor" in TestKit
// 			val printer = system.actorOf(Props(new Printer with TimingDiagnostics with WireTap {
// 				def listener = testActor															//$ testActor is part of TestKit //$
// 				}), "printer")

// 			// function to create new actors (Forwarder actors that forward to Printer actors and use TimingDiagnostics)
// 			def createDiagnostic = new Forwarder(printer) with TimingDiagnostics

// 			// create several actors
// 			val actor1 = system.actorOf(Props(createDiagnostic), "t-1")
// 			val actor2 = system.actorOf(Props(createDiagnostic), "t-2")
// 			val actor3 = system.actorOf(Props(createDiagnostic), "t-3")
// 			val routees = Vector[ActorRef](actor1, actor2, actor3)
			
// 			// put them in a specific type of router. BroadcastRouter "broadcasts" messages to all actors within.
// 			val router = system.actorOf(Props(createDiagnostic).withRouter(
// 				BroadcastRouter(routees = routees)), "router-to-transformers")
// 			val transformerWithRouter = system.actorOf(Props(new Forwarder(router) with TimingDiagnostics), "transformer-with-router")
// 			val transformer = system.actorOf(Props(new Forwarder(transformerWithRouter) with TimingDiagnostics), "first-transformer")

// 			//												actor1	--> print
// 			// transformer --> transformerWithRouter --> 	actor2	--> print
// 			//												actor3  --> print

// 			// send some text, starting the chain of events
// 			//transformer ! "Some random message"
// 			transformer ! TraceMessage(1, "some text to play with")
// 			expectMsg(TraceMessage(1, "some text to play with"))

// 			// create promise/future pair to be used below
// 			val p = Promise[Seq[DiagnosticsData[(Long, Long)]]]																				//$ These can be created and used in pairs. This is a hook into the actor eventually finishing.  This way we can hang on to results. //$
// 			val future = p.future

// 			// create a return address that the spiders will send diagnostic data to
// 			val returnAddress = system.actorOf(Props(new Actor {
// 				var results = List[DiagnosticsData[(Long, Long)]]()
// 				def receive = {
// 					// This is the only type of message that should be received
// 					case m: Any => // this should be "case DiagnosticsData[(Long, Long)] but getting erasure"
// 						results = results :+ m.asInstanceOf[DiagnosticsData[(Long,Long)]]
// 						if (results.size == 6) 
// 							p.success(results)								//$ Note that this is a cheat and that "in a real system you would work with what you have at a certain moment in time" //$
// 				}
// 			}))

// 			// this is the request for diagnostics data.  It could have been sent to any actor in the web that extends the diagnostics trait.
// 			printer ! (TimeDataRequest(1), Spider(returnAddress))
// 			val timingData = Await.result(future, 1 seconds)
// 			timingData.map(_.data._1 must be (1))
// 			println(timingData.mkString("\n"))

// 		}
// 	}



// 	/**
// 	*	This is the test with basic actors
// 	**/
// 	println("\nTesting sending 10,000 messages " + numTestRuns + " times through BASIC actors...")
// 	val basicPrinter = system.actorOf(Props[Printer], "basicPrinter")
// 	val countingForwarder = system.actorOf(Props(new CountingForwarder(basicPrinter)), "countingForwarder")
// 	val basicForwarder = system.actorOf(Props(new Forwarder(countingForwarder)), "basicForwarder")

// 	var basicTest = {
// 		var future = basicForwarder ? "Some random text"
// 		var result = Await.result(future, 1 second)
// 		future
// 	}

// 	var timeOne = new ListBuffer[Long]()
// 	for(i <- 1 to numTestRuns) {
// 		var (basicTestResult, basicTestTime) = helpers.time { basicTest }
// 		timeOne += basicTestTime
// 	}
// 	println("num results found: " + timeOne.length)
// 	println("average times for tests: " + (( timeOne.reduceLeft(_+_)) / ( timeOne.length)) + "ns")

// 	override protected def afterAll() {
// 		super.afterAll()
// 		system.shutdown()
// 	}


// 	/**
// 	*	Testing messages with diagnostic actors
// 	**/
// 	println("\nTesting sending 10,000 messages " + numTestRuns + " times through TRACED actors...")
// 	val tracedPrinter = system.actorOf(Props(new Printer with TimingDiagnostics), "tracingPrinter")
// 	val tracedForwarderCounting = system.actorOf(Props(new CountingForwarder(tracedPrinter) with TimingDiagnostics), "tracedForwarderCounting")
// 	val tracedForwarder = system.actorOf(Props(new Forwarder(tracedForwarderCounting) with TimingDiagnostics), "tracedForwarder")

// 	var tracingTest = {
// 		var future = tracedForwarder ? TraceMessage(2, "Here's a traced Message")
// 		var result = Await.result(future, 1 second)
// 		future
// 	}

// 	var timeTwo = new ListBuffer[Long]()
// 	for(i <- 1 to numTestRuns) {
// 		var (tracingTestResult, tracingTestTime) = helpers.time { tracingTest }
// 		timeTwo += tracingTestTime
// 	}

// 	println("num results found: " + timeTwo.length)
// 	println("average times for tests: " + ((timeTwo.reduceLeft(_+_)) / (timeTwo.length)) + "ns")


// }