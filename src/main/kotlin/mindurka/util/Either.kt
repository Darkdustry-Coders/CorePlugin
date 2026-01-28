package mindurka.util

import mindurka.annotations.PublicAPI

@PublicAPI
sealed class Either2<A: Any, B: Any> {
    companion object {
        @PublicAPI @JvmStatic fun <A: Any, B: Any> ofA(value: A) = Either2.A<A, B>(value)
        @PublicAPI @JvmStatic fun <A: Any, B: Any> ofB(value: B) = Either2.B<A, B>(value)
    }

    @PublicAPI abstract fun isA(): Boolean
    @PublicAPI abstract fun isB(): Boolean
    @Throws(IllegalStateException::class)
    @PublicAPI abstract fun a(): A
    @Throws(IllegalStateException::class)
    @PublicAPI abstract fun b(): B

    class A<A: Any, B: Any>(val value: A): Either2<A, B>() {
        override fun isA() = true
        override fun isB() = false
        override fun a() = value
        override fun b() = throw IllegalStateException("Calling 'b()' on an 'A' value")
    }

    class B<A: Any, B: Any>(val value: B): Either2<A, B>() {
        override fun isA() = false
        override fun isB() = true
        override fun a() = throw IllegalStateException("Calling 'b()' on an 'A' value")
        override fun b() = value
    }
}