package mindurka.annotations

/**
 * An entry in the database.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class DatabaseEntry(
    val value: String,
)

/**
 * An event that is sent over the network.
 *
 * Unlike regular events, a network event is not immediately
 * dispatched to the application, instead being sent to the broker.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class PrimaryKey
