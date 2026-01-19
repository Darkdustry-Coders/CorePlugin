package mindurka.util

interface Write<E : Exception?> {
    fun i(value: Int)
    fun l(value: Long)
    fun f(value: Float)
    fun sym(value: String?)
    fun nil()
}
