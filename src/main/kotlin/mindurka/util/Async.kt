// So glad to not be able to google any of this.

@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package mindurka.util

import mindurka.annotations.PublicAPI
import java.util.concurrent.CompletableFuture
import arc.func.Prov
import arc.Core
import arc.util.Log
import arc.util.Strings
import arc.util.Threads
import arc.util.io.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.newCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import mindurka.coreplugin.sessionData
import kotlin.coroutines.CoroutineContext
import mindustry.Vars
import mindustry.core.Version
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Host
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

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
    @JvmField
    val mainScope = MainScope()

    /**
     * Dispatch a `suspend fn`.
     */
    @PublicAPI
    @JvmStatic
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
    @JvmStatic
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

    /**
     * Send a GET request to a website and return the contents
     * as a string.
     */
    @PublicAPI
    @JvmStatic
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
    @JvmStatic
    suspend fun pingHost(address: String, port: Int): Host {
        val future = CompletableFuture<Host>()
        
        Vars.net.pingHost(
            address,
            port,
            { future.complete(it) },
            { future.completeExceptionally(it) })

        return future.await()
    }

    /**
     * Attempt to connect the player to a server.
     *
     * @return `false` if any of the checks have failed, `true` otherwise.
     */
    @PublicAPI
    @JvmStatic
    suspend fun connect(player: Player, address: String, port: Int): Boolean {
        val host = try {
            pingHost(address, port)
        } catch (_: Exception) {
            return false
        }
        if (host.version != Version.build) return false
        if (player.con.hasDisconnected) return false

        Log.info("Sending player ${Strings.stripColors(player.name)} to $address:$port")

        try {
            player.sessionData.playerLeft(player)
            Call.connect(player.con, address, port)
            player.con.close()

            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            exitProcess(1)
        }
    }
}