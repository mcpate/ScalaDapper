package TraceBuilder
import TraceType._
import akka.actor.{Actor, ActorRef, ActorPath, ActorContext, Props}
import scala.util.Random
import java.util.UUID


case class TraceBuilder(sampleRatioUpper: Int) {

	// rate (.01f = 1%) per 10,000 msgs (see sampleTrace)
	val sampleRatioPercent = 0.5f

	def buildTrace(m: Any) = {
		if(sampleTrace == true) {
			println("In TraceBuilder: building trace for sampled msg.")
			TraceType(UUID.randomUUID(), m, true)
		} else {
			println("In TraceBuilder: building trace for NON-sampled msg.")
			TraceType(UUID.randomUUID(), m, false)
		}
	}	

	def sampleTrace: Boolean = {
		val generator = Random		
		generator.nextInt(sampleRatioUpper) < sampleRatioUpper*sampleRatioPercent
	}


}