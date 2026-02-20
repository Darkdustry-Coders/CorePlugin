// So glad to not be able to google any of this.

@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package mindurka.util

import mindurka.annotations.PublicAPI
import java.util.concurrent.CompletableFuture
import arc.func.Prov
import arc.Core
import arc.util.Log
import arc.util.Threads
import arc.util.io.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import mindustry.Vars
import mindustry.net.Host
import java.net.HttpURLConnection
import java.net.URI

internal class MainMindustryDispatcher: MainCoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Core.app.post { try {
            block.run()
        } catch (_: CancellationException) {
            // Don't care.
        } catch (t: Throwable) {
            Log.err(t)
        } }
    }

    override val immediate = this
}

internal class MindustryDispatcherFactory: MainDispatcherFactory {
    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher = MainMindustryDispatcher()

    override fun hintOnError(): String = "For tests Dispatchers.setMain from kotlinx-coroutines-test module can be used"

    override val loadPriority: Int
        get() = Int.MAX_VALUE / 2
}

/**
 * Async API.
 *
 * Aka attempt to work around suspend fns.
 *
 * Futures should be evaluated whether they are awaited or not.
 */
@PublicAPI
@OptIn(ExperimentalCoroutinesApi::class)
object Async {
    /**
     * Main async scope for CorePlugin.
     *
     * This scope wraps over [arc.Application.post] for dispatch.
     */
    @PublicAPI
    @JvmStatic
    val mainScope = MainScope()

    /**
     * Dispatch a `suspend fn`.
     */
    @PublicAPI
    fun run(fn: suspend() -> kotlin.Unit) {
        mainScope.launch(mainScope.newCoroutineContext(mainScope.coroutineContext)) {
            try {
                fn()
            } catch (why: Throwable) {
                Log.err(why)
                Runtime.getRuntime().exit(1)
            }
        }
    }

    /**
     * Run `fn` in a separate thread as a `suspend fn`.
     *
     * To be used exclusively to safely execute blocking operations.
     */
    @PublicAPI
    suspend fun <T> thread(fn: Prov<T>): T = suspendCancellableCoroutine { continuation ->
        val handle = Threads.thread {
            try {
                val value = fn.get()
                Core.app.post { continuation.completeResume(value as Any) }
            } catch (_: InterruptedException) {}
              catch (e: Exception) {
                Core.app.post { continuation.cancel(e) }
            }
        }

        continuation.invokeOnCancellation {
            handle.interrupt()
        }
    }

    @PublicAPI
    suspend fun fetchHttpString(req: String): String {
        return thread {
            // No, IDEA, this is blocking context.
            // It's literally in the definition of `thread`.
            val connection = URI(req).toURL().openConnection() as HttpURLConnection
            connection.doInput = true;
            connection.connect()
            Streams.copyString(connection.getInputStream())
        }
    }
}

/**
 * Asyncified calls.
 */
@PublicAPI
object AsyncCall {
    @PublicAPI
    fun pingHost(address: String, port: Int): CompletableFuture<Host> {
        val future = CompletableFuture<Host>()
        
        Vars.net.pingHost(
            address,
            port,
            { future.complete(it) },
            { future.completeExceptionally(it) })

        return future
    }
}