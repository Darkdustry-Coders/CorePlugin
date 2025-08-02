package mindurka.annotations

import kotlin.reflect.KClass

/**
 * An event that is sent over the network.
 *
 * Unlike regular events, a network event is not immediately
 * dispatched to the application, instead being sent to the broker.
 */
@PublicAPI
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkEvent(
    val value: String,
    val ttl: Int = 60,
)

/**
 * An event that acts as a request for more events.
 *
 * A '@NetworkRequest' class must be annotated with `@NetworkEvent`.
 */
@PublicAPI
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkRequest(
    val value: KClass<*>,
)
