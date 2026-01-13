package mindurka.api

import mindurka.annotations.PublicAPI
import arc.util.Timer as ArcTimer

/**
 * Run after time specified in seconds.
 *
 * All durations are provided in seconds.
 */
@PublicAPI
fun timer(seconds: Float, lifetime: Lifetime = Lifetime.Forever, run: Runnable): Cancel
    = Timer.timer(seconds, lifetime, run)

/**
 * Repeat task until it's cancelled.
 *
 * All durations are provided in seconds.
 */
@PublicAPI
fun interval(interval: Float, delay: Float = 0f, lifetime: Lifetime = Lifetime.Forever, run: Runnable): Cancel
    = Timer.interval(interval, delay, lifetime, run)

@PublicAPI
object Timer {
    @PublicAPI
    @JvmStatic
    fun schedule(runnable: Runnable, delaySeconds: Float): Cancel {
        val task = ArcTimer.schedule(runnable, delaySeconds)
        return Cancel { task.cancel() }
    }
    @PublicAPI
    @JvmStatic
    fun schedule(runnable: Runnable, delaySeconds: Float, intervalSeconds: Float): Cancel {
        val task = ArcTimer.schedule(runnable, delaySeconds, intervalSeconds)
        return Cancel { task.cancel() }
    }

    /**
     * Run after time specified in seconds.
     *
     * All durations are provided in seconds.
     */
    @PublicAPI
    @JvmStatic
    @JvmOverloads
    fun timer(seconds: Float, lifetime: Lifetime = Lifetime.Forever, run: Runnable): Cancel {
        val timer = schedule(run, seconds)
        lifetime.bind(timer)
        return timer
    }

    /**
     * Repeat task until it's cancelled.
     *
     * All durations are provided in seconds.
     */
    @PublicAPI
    @JvmStatic
    @JvmOverloads
    fun interval(interval: Float, delay: Float = 0f, lifetime: Lifetime = Lifetime.Forever, run: Runnable): Cancel {
        val timer = schedule(run, delay, interval)
        lifetime.bind(timer)
        return timer
    }
}