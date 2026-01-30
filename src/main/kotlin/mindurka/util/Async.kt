// So glad to not be able to google any of this.

@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package mindurka.util

import mindurka.annotations.PublicAPI
import java.util.concurrent.CompletableFuture
import arc.func.Prov
import arc.Core
import arc.util.Log
import kotlinx.coroutines.CancellationException
import java.util.WeakHashMap
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.newCoroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.Continuation
import mindustry.gen.Call
import mindustry.Vars
import mindustry.net.Host

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
    @PublicAPI
    @JvmStatic
    val mainScope = MainScope()

    @PublicAPI
    @JvmName("runKtSuspend")
    fun <T> run(fn: suspend() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        mainScope.launch(mainScope.newCoroutineContext(mainScope.coroutineContext)) {
            try {
                future.complete(fn())
            } catch (why: Throwable) {
                future.completeExceptionally(why)
            }
        }
        return future
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
