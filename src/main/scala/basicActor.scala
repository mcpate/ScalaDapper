import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask //This allows usage of "?" message sending
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer


									
	
object Main extends App {
		val system = ActorSystem("TestSystem")
		implicit val timeout = Timeout(5 seconds)
		val numTimes = 1000


		/**
		 * Test blocking calls to actor that responds 
		**/
		val emptyActorSync = system.actorOf(Props[EmptyActor])		
		val test1 = helpers.time({ for( i <- 1 to numTimes ) {
			val future = emptyActorSync ? "someRandomMessage"  // "?" means "will return a future"
			val result = Await.result(future, timeout.duration)
		}
		})


		/**
		 * Test simple firing off of messages to actor that responds: Asyncronous
		**/
		val emptyActorAsync = system.actorOf(Props[EmptyActor])
		val test2 = helpers.time({ for( i <- 1 to numTimes ) {
			emptyActorAsync ! "someRandomMessage"
		}
		})


			
		/**
		 * Async messages to actor that creates a child an waits on them to respond
		**/
		val spawnActorSync = system.actorOf(Props( new SpawnActor(false) ))
		val test3 = helpers.time({ for( i <- 1 to numTimes ) {
			spawnActorSync ! "spawn"
		}
		})


		/**
		*	Async messages to actor that creates a child and does not wait on child's response
		**/
		val spawnActorAsync = system.actorOf(Props( new SpawnActor(true) ))
		val test4 = helpers.time({ for( i <- 1 to numTimes ) {
			spawnActorAsync ! "spawn"
		}
		})



		system.shutdown()
}


/**
*	Misc. helper funcitons
**/
object helpers {

	def time[X](block: => X): X = {
	  val t0 = System.nanoTime()
	  val result = block
	  val t1 = System.nanoTime()
	  println("Elapsed time: " + (t1 - t0) + "ns")
	  result
	}
}
	

/**
*	Basic actors for use in the tests
**/
class EmptyActor extends Actor {
	def receive = {
		case _ => sender ! "Got It"
	}
}

	
class SpawnActor(async: Boolean)(implicit val timeout: Timeout) extends Actor {
	def receive = {
		case _ => {
			val child = context.actorOf(Props[EmptyActor])
			if( async ) {
				child ! "someRandomMessage"	
			} else {
				val future = child ? "someRandomMessage"
				val result = Await.result(future, timeout.duration)
			}
		}	
	}
}


