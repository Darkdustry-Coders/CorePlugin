package mindurka.util

fun unreachable(): Nothing = throw UnreachableException()
class UnreachableException(message: String = "Unreachable reached!"): RuntimeException(message)
