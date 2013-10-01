
// import SpiderDiagnostics._
// import SpiderPattern._
// import akka.actor.{Actor, ActorRef, ActorSystem, Props}
// //import akka.dispatch.{Future, Promise, Await}
// import scala.concurrent.{Future, Promise, Await}
// import akka.testkit.TestKit
// import org.scalatest.{WordSpecLike, BeforeAndAfterAll}			//$ Wordspec allows BDD style wording, BeforeAnd... allows setup and teardown tasks //$
// import org.scalatest.matchers.ShouldMatchers					//$ MustMatchers provides wording
// import akka.util.Timeout
// import scala.concurrent.duration._
// import akka.routing.BroadcastRouter
// import scala.language.postfixOps								//$ Allows "1 seconds" vs "1.seconds"
// import akka.pattern.ask
		
// /**
// *	Misc. helper funcitons
// **/
// object helpers {

// 	def time[X](block: => X): X = {
// 	  val t0 = System.nanoTime()
// 	  val result = block
// 	  val t1 = System.nanoTime()
// 	  println("Elapsed time: " + (t1 - t0) + "ns")
// 	  result
// 	}
// }

// /**
// *	Stuff for testing including Actors
// **/
// case class SomeMessage(id: Long, text: String) extends HasId

// trait WireTap extends Actor {
// 	def listener: ActorRef

// 	abstract override def receive = {
// 		case m =>
// 			super.receive(m)
// 			listener ! m
// 	}
// }

// class Transformer(next: ActorRef) extends Actor with Node {
// 	def receive = {
// 		case m: SomeMessage =>
// 			next ! m.copy() 
// 		case m: String => next forward m
// 	}
// }

// class Printer extends Actor {
// 	def receive = {
// 		case m: SomeMessage => println( m.text )
// 		case m: String => 
// 			sender ! "done in printer"
// 	}
// }

// class SpiderTest extends TestKit(ActorSystem("spider")) with WordSpecLike with ShouldMatchers with BeforeAndAfterAll {
	
// 	implicit val timeout = Timeout(10 seconds)

// 	"The spider " should {
// 		"collect data about specific events " in {

// 			val numActors = 10

// 			/**
// 			*	The actors being created in this first part of the test are all a part of forming the following topology
// 			*
// 			*												actor1	--> printer
// 			* transformer --> transformerWithRouter --> 	actor2	--> printer
// 			*												...
// 			*												actorN  --> printer
// 			**/

// 			// create a printer actor that fires messages to the "testActor" in TestKit
// 			val printer = system.actorOf(Props(new Printer with TimingDiagnostics with WireTap {
// 				def listener = testActor															//$ testActor is part of TestKit //$
// 				}), "printer")

// 			// function to create new actors (Transformer actors that forward to Printer actors and use TimingDiagnostics)
// 			def createDiagnostic = new Transformer(printer) with TimingDiagnostics

// 			// create a router
// 			val router = system.actorOf(Props(createDiagnostic).withRouter(
// 				BroadcastRouter(nrOfInstances = numActors)), "router")

// 			// create transformer to router
// 			val transformerWithRouter = system.actorOf(Props(new Transformer(router) with TimingDiagnostics), "transformer-with-router")
			
// 			// create initial transformer to transformer to router.
// 			val transformer = system.actorOf(Props(new Transformer(transformerWithRouter) with TimingDiagnostics), "first-transformer")

// 			/**
// 			*	The following starts testing actors
// 			*/

// 			// test printer actor verifying that the TestKit actor is being reached.
// 			printer ! SomeMessage(1, "test printer")
// 			expectMsg(SomeMessage(1, "test printer"))

// 			// send message to first transformer which forwards to above topology
// 			transformer ! (SomeMessage(1, "some text to play with"))
// 			expectMsg(SomeMessage(1, "some text to play with"))

// 			// create promise/future pair to be used below
// 			val p = Promise[Seq[DiagnosticsData[(Long, Long)]]]																				//$ These can be created and used in pairs. This is a hook into the actor eventually finishing.  This way we can hang on to results. //$
// 			val future = p.future

// 			// create a return address that the spiders will send diagnostic data to.
// 			case class DiagnosticsDataEF(diagnosticsData: DiagnosticsData[(Long, Long)]) 
// 			val returnAddress = system.actorOf(Props(new Actor {
// 			 	var results = List[DiagnosticsData[(Long, Long)]]()
// 			 	def receive = {
// 			 		case m: DiagnosticsData[(Long, Long)] =>
// 			 			results = results :+ m
// 			 			if (results.size == numActors) p.success(results)
// 			 	}
// 			}))

// 			printer ! (TimeDataRequest(1), Spider(returnAddress))
// 			val timingData = Await.result(future, 5 seconds)
// 			timingData.map(_.data._1 should be (1))
// 			println(timingData.mkString("\n"))


// 			//====================================================================================
// 			// Time to send a message through all actors in the above topology and get a message
// 			// back saying that the original message was received.
			
// 			val p2 = Promise[Boolean]
// 			val f2 = p2.future
			
// 			val res2 = system.actorOf(Props(new Actor {
// 				var results = List[Int]()
// 				def receive = {
// 					case "done in printer" =>
// 					println("message rec")
// 					results = results :+ 1
// 					if (results.size == numActors/2) p2.success(true)
// 				}
// 			}))

// 			val results = helpers.time {
// 				transformer.send("are you done", res2)
// 				Await.result(f2, 5 seconds)
// 			}


// 		}
// 	}

// 	override protected def afterAll() {
// 		super.afterAll()
// 		system.shutdown()
// 	}
// }