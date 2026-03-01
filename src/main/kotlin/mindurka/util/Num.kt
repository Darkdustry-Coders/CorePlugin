package mindurka.util

inline fun <T> UInt.ifCheckedSub(other: UInt, then: (UInt) -> T): T? = if (this >= other) then(this - other) else null