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
 * Automatically increment the value.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class Autoincrement

/**
 * Automatically generate a snowflake key.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class Autosnowflake

/**
 * Assigned value must never appear again on any other entry.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class Unique
