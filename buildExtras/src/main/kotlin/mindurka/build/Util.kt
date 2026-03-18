package mindurka.build

import mindurka.annotations.PublicAPI

/**
 * Generic result type.
 */
@PublicAPI
abstract class Result<T, E> {
    /** Check if the operation was successful. */
    val isOk: Boolean = this is Ok
    /** Check if the operation has failed. */
    val isErr: Boolean = this is Err

    abstract fun unwrap(): T
    abstract fun unwrapErr(): E

    abstract fun asOk(): Ok<T, E>?
    abstract fun asErr(): Err<T, E>?

    inline fun <Y> select(ifOk: (T) -> Y, ifErr: (E) -> Y): Y = asOk()?.let { ifOk(it.value) } ?: ifErr(unwrapErr())

    data class Ok<T, E>(val value: T): Result<T, E>() {
        override fun unwrap() = value
        override fun unwrapErr() = throw IllegalStateException("'unwrapErr()' on an 'Ok' value")

        override fun asOk(): Ok<T, E> = this
        override fun asErr() = null
    }

    data class Err<T, E>(val error: E): Result<T, E>() {
        override fun unwrap() = throw IllegalStateException("'unwrap()' on an 'Err' value")
        override fun unwrapErr() = error

        override fun asOk() = null
        override fun asErr(): Err<T, E> = this
    }
}