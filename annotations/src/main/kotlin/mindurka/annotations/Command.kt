package mindurka.annotations
import kotlin.reflect.KClass

/**
 * A command that can be executed via console.
 *
 * This annotation can only be applied to static functions.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConsoleCommand(val value: String = "<infer>")
/**
 * A command that can be executed in-game.
 *
 * This annotation can only be applied to static functions.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Command(val value: String = "<infer>")

/**
 * Marks an argument as optional.
 *
 * Java equivalent of Kotlin's nullable type.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Maybe
/**
 * Collect the rest of the input.
 *
 * Can only be one parameter at the end.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Rest
/**
 * Specify the type of the list.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ListOf(val value: KClass<*>)

/**
 * Only allow admins to run the command.
 *
 * Cannot be used with `@ConsoleCommand`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdminOnly
/**
 * Rate limit the command.
 *
 * Cannot be used with `@ConsoleCommand`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Cooldown(val value: Float)
/**
 * Hide the command from /help.
 *
 * Cannot be used with `@ConsoleCommand`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Hidden
/**
 * Minimum permission level required to execute the command.
 *
 * Currently, defined permission levels are as follows:
 * - 0: Normal User
 * - 100: Moderator
 * - 200: Admin
 * - 300: Admin Overseer
 * - 1000: Console
 *
 * Cannot be used with `@ConsoleCommand`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPermission(val value: Int)
