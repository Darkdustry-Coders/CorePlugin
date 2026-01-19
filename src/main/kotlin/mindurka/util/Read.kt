package mindurka.util

interface Read<E : Exception> {
    fun i(): Int
    fun l(): Long
    fun f(): Float
    fun sym(): String?
    fun nil(): Boolean
}
