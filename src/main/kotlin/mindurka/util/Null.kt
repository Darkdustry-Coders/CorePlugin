package mindurka.util

import mindurka.annotations.PublicAPI

@RequiresOptIn(message = "'nodecl' allows bypassing Kotlin's null safety. This may cause problems at runtime.")
@Retention(AnnotationRetention.BINARY)
annotation class UnsafeNull

@UnsafeNull
@PublicAPI
@Suppress("UNCHECKED_CAST")
fun <T> nodecl(): T = null as T

/** A static object to replace [kotlin.Unit]. */
@PublicAPI
object K

/** A reference to a value. */
@PublicAPI
data class Ref<T>(var r: T) {
    override fun toString(): String = r?.toString() ?: "null"
    override fun hashCode(): Int = r?.hashCode() ?: 0
    override fun equals(other: Any?): Boolean = (if (other is Ref<*>) r?.equals(other.r) else r?.equals(other)) ?: true
}
