package TraceType

import java.util.UUID


case class TraceType(uuid: UUID, msg: Any, sampled: Boolean) {

	val m = msg

	def removeTrace = {
		m
	}


}
