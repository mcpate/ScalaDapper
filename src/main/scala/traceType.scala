package TraceType

import java.util.UUID


case class TraceType(uuid: UUID, msg: Any, sampled: Boolean)
