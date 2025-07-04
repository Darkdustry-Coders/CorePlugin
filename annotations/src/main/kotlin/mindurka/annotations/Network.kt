package mindurka.annotations

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
