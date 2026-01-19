package mindurka.util

class FormatException : RuntimeException {
    constructor(message: String?) : super(message)
    constructor(message: String?, source: Throwable?) : super(message, source)
}
