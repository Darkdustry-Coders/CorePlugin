package mindurka.annotations

/**
 * A mark for codegen to wrap a method in [mindurka.util.Async.run].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PublicAPI
annotation class Awaits
