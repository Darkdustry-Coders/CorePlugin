package mindurka.coreplugin.database

import arc.Core
import arc.struct.ByteSeq
import arc.struct.Seq
import arc.util.Log
import arc.util.io.Streams
import kotlinx.coroutines.future.await
import mindurka.api.sleep
import mindurka.config.SharedConfig
import mindurka.coreplugin.publicKey
import mindurka.util.Async
import mindustry.Vars
import mindustry.gen.Player
import net.buj.surreal.Driver
import net.buj.surreal.EventCallback
import net.buj.surreal.Query
import net.buj.surreal.Response
import net.buj.surreal.SurrealURL
import net.buj.surreal.SimpleDebugHandler
import java.io.OutputStream
import java.io.PrintStream
import java.lang.Exception
import java.security.PublicKey
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.io.encoding.Base64

data class PlayerSmallData (
    val id: String,
    var keySet: Boolean,
    // TODO: Mutes.
)

class MergedAccountException: Exception("Unhandled merged account exception")
class DisabledAccountException: Exception("Unhandled disconnected account exception")
class DisconnectedAccountException: Exception("Unhandled disconnected account exception")
class SharedAccountException: Exception("Unhandled shared account exception")
class KeyValidationFailure: Exception("Unhandled key validation failure")

object Database {
    private val initScript = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/init.surrealql"))
    private val loaduserScript = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/loaduser.surrealql"))
    private val setkeyScript = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/setkey.surrealql"))

    private val playerData = WeakHashMap<Player, PlayerSmallData>()

    private var driver: Driver? = null

    private abstract class Queued {
        abstract fun submit(driver: Driver)
    }
    private data class QueuedSingle(
        val query: Query,
        val future: CompletableFuture<Response>,
    ): Queued() {
        override fun submit(driver: Driver) {
            driver.querySingle(query, object : EventCallback<Response> {
                override fun run(p0: Response) = Core.app.post { future.complete(p0) }
                override fun fail(p0: Exception) = Core.app.post { future.completeExceptionally(p0) }
            })
        }
    }
    private data class QueuedMulti(
        val query: Query,
        val future: CompletableFuture<Array<Response>>,
    ): Queued() {
        override fun submit(driver: Driver) {
            driver.query(query, object : EventCallback<Array<Response>> {
                override fun run(p0: Array<Response>) = Core.app.post { future.complete(p0) }
                override fun fail(p0: Exception) = Core.app.post { future.completeExceptionally(p0) }
            })
        }
    }

    private var queue: Seq<Queued>? = Seq.with()

    internal fun load() {
        val url = SurrealURL(SharedConfig.i.surrealDbUrl)

        Async.run {
            while (true) {
                try {
                    driver = createDriver(url).await()
                    Log.info("Connected to the database")
                    break
                } catch (err: Exception) {
                    Log.err("Connection to database failed", err)
                    Log.err("Retrying in 5s...")
                    sleep(5f).await()
                }
            }

            driver!!.query(Query(initScript)).await().ok()
            Log.info("Initialized successfully")

            queue?.let { queue ->
                for (req in queue) {
                    req.submit(driver!!)
                }
            }
            queue = null
        }
    }

    private fun createDriver(url: SurrealURL): CompletableFuture<Driver> {
        val driver = Driver(url)
        driver.debug = SimpleDebugHandler(PrintStream(object : OutputStream() {
            private val bytes = ByteSeq(2048)

            override fun write(b: Int) {
                bytes.add(b.toByte())

                prints()
            }

            override fun write(b: ByteArray?) {
                assert(b != null)
                bytes.addAll(b, 0, b!!.size)

                prints()
            }

            override fun write(b: ByteArray?, off: Int, len: Int) {
                assert(b != null)
                bytes.addAll(b, off, len)

                prints()
            }

            private fun prints() {
                while (true) {
                    val end = bytes.indexOf('\n'.code.toByte());
                    if (end == -1) break

                    val s = String(bytes.items, 0, end, Vars.charset)
                    Log.debug(s)

                    bytes.removeRange(0, end)
                }
            }
        }))
        val future = CompletableFuture<Driver>()
        driver.onConnect(object : EventCallback<Any> {
            override fun run(dummy: Any) = Core.app.post { future.complete(driver) }
            override fun fail(why: Exception) = Core.app.post { future.completeExceptionally(why) }
        })
        return future
    }

    internal fun abstractQuery(query: Query): CompletableFuture<Array<Response>> {
        if (queue != null) {
            val future = CompletableFuture<Array<Response>>()
            queue!!.add(QueuedMulti(query, future))
            return future;
        } else if (driver == null) {
            val future = CompletableFuture<Array<Response>>()
            future.completeExceptionally(IllegalStateException("'queue' and 'driver' cannot be null at the same time!"))
            return future;
        } else {
            return driver!!.query(query)
        }
    }

    internal fun abstractQuerySingle(query: Query): CompletableFuture<Response> {
        if (queue != null) {
            val future = CompletableFuture<Response>()
            queue!!.add(QueuedSingle(query, future))
            return future;
        } else if (driver == null) {
            val future = CompletableFuture<Response>()
            future.completeExceptionally(IllegalStateException("'queue' and 'driver' cannot be null at the same time!"))
            return future;
        } else {
            return driver!!.querySingle(query)
        }
    }

    @Throws(KeyValidationFailure::class, DisconnectedAccountException::class, MergedAccountException::class,
        SharedAccountException::class, DisabledAccountException::class)
    suspend fun login(uuid: String, key: PublicKey?): PlayerSmallData {
        val query = abstractQuerySingle(Query(loaduserScript).x("uuid", uuid).x("key", key?.encoded?.let(
            Base64.withPadding(Base64.PaddingOption.ABSENT)::encode
        ))).await().ok()

        when (val error = query.result.at("disabled").asInteger()) {
            0 -> {}
            1 -> throw MergedAccountException()
            2 -> throw DisconnectedAccountException()
            3 -> throw KeyValidationFailure()
            4 -> throw DisabledAccountException()
            5 -> throw SharedAccountException()
            else -> {
                throw RuntimeException("Unexpected login error: $error")
            }
        }

        return PlayerSmallData(
            query.result.at("id").asString(),
            query.result.at("keySet").asBoolean(),
        )
    }

    internal fun setPlayerData(player: Player, data: PlayerSmallData) { playerData[player] = data }
    fun localPlayerData(player: Player): PlayerSmallData = playerData[player] ?: throw NullPointerException("Player data must not be null!")

    suspend fun setKey(player: Player) {
        val data = playerData[player] ?: return
        val key = player.publicKey ?: return
        try {
            abstractQuery(Query(setkeyScript).x("id", data.id).x("key", Base64.withPadding(Base64.PaddingOption.ABSENT).encode(key.encoded))).await().ok()
        } catch (e: Throwable) {
            player.sendMessage("[scarlet]An error has occurred while processing command.")
            Log.err(e)
            return
        }
        data.keySet = true
    }
}

fun Driver.query(query: Query): CompletableFuture<Array<Response>> {
    val future = CompletableFuture<Array<Response>>()
    query(query, object : EventCallback<Array<Response>> {
        override fun run(value: Array<Response>) = Core.app.post { future.complete(value) }
        override fun fail(why: Exception) = Core.app.post { future.completeExceptionally(why) }
    })
    return future
}

fun Driver.querySingle(query: Query): CompletableFuture<Response> {
    val future = CompletableFuture<Response>()
    querySingle(query, object : EventCallback<Response> {
        override fun run(value: Response) = Core.app.post { future.complete(value) }
        override fun fail(why: Exception) = Core.app.post { future.completeExceptionally(why) }
    })
    return future
}

fun Array<Response>.ok(): Array<Response> {
    for (response in this) response.ok()
    return this
}