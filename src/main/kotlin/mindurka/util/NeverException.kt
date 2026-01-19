package mindurka.util

/** An exception that cannot be constructed, thus never be thrown.  */
class NeverException private constructor() : RuntimeException()
