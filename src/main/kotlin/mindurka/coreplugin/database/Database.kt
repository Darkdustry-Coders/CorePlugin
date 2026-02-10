package mindurka.coreplugin.database

import arc.Core
import arc.struct.ByteSeq
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.io.Streams
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mindurka.api.Cancel
import mindurka.api.sleep
import mindurka.config.SharedConfig
import mindurka.coreplugin.Config
import mindurka.coreplugin.publicKey
import mindurka.util.Async
import mindurka.util.UnreachableException
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.gen.Player
import net.buj.surreal.Driver
import net.buj.surreal.EventCallback
import net.buj.surreal.LiveResponse
import net.buj.surreal.Query
import net.buj.surreal.Response
import net.buj.surreal.SurrealURL
import net.buj.surreal.SimpleDebugHandler
import java.io.OutputStream
import java.io.PrintStream
import java.lang.Exception
import java.security.MessageDigest
import java.security.PublicKey
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import kotlin.io.encoding.Base64
import kotlin.jvm.javaClass
import kotlin.system.exitProcess

data class PlayerSmallData (
    val userId: String,
    val id: String,
    var keySet: Boolean,
    var shortId: Int?,
    var permissionLevel: Int,
    // TODO: Mutes.
) {
    suspend fun setPermissionLevel(player: Player, level: Int) {
        permissionLevel = level
        if (permissionLevel < 0) permissionLevel = 0
        if (permissionLevel > 1000) permissionLevel = 1000
        player.admin = permissionLevel >= 100
        Vars.netServer.admins.getInfo(player.uuid()).admin = permissionLevel >= 100
        Database.abstractQuery(Query(DatabaseScripts.setpermissionlevelScript)
            .x("permissionLevel", permissionLevel).x("id", id)).ok()
    }
}

class MergedAccountException: Exception("Unhandled merged account exception")
class DisabledAccountException: Exception("Unhandled disconnected account exception")
class DisconnectedAccountException: Exception("Unhandled disconnected account exception")
class SharedAccountException: Exception("Unhandled shared account exception")
class KeyValidationFailure: Exception("Unhandled key validation failure")
class BannedAccountException(val admin: String, val reason: String, val expires: Instant, val server: String): Exception("Unhandled ban")
class KickedAccountException(val admin: String, val reason: String, val expires: Instant): Exception("Unhandled kick")
class GraylistedAccountException: Exception("Unhandled graylist")

data class BannedInfo(
    val ips: Seq<String>,
    val key: Seq<ByteArray>,
    val admin: String,
    val reason: String,
    val expires: Instant,
    val server: String
)

data class KickedInfo(
    val ips: Seq<String>,
    val key: Seq<ByteArray>,
    val admin: String,
    val reason: String,
    val expires: Instant,
)

internal object DatabaseScripts {
    val initScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/init.surrealql"))
    val loaduserScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/loaduser.surrealql"))
    val setkeyScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/setkey.surrealql"))
    val setpermissionlevelScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/setpermissionlevel.surrealql"))

    val ispsFetchScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/isps_fetch.surrealql"))
    val ispsUpdateScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/isps_update.surrealql"))
}

object Database {
    val banCache = ObjectMap<String, BannedInfo>()
    val kickCache = ObjectMap<String, KickedInfo>()

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
    private data class QueuedLive(
        val id: String,
        val cb: suspend (LiveResponse) -> kotlin.Unit,
    ): Queued() {
        override fun submit(driver: Driver) {
            driver.onLive(id, cb)
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

            try {
                if (SharedConfig.i.initDb) {
                    driver!!.query(Query(DatabaseScripts.initScript)).await().ok()
                    Log.info("Initialized successfully")
                } else Log.info("Skipping database initialization")

                queue?.let { queue ->
                    for (req in queue) {
                        req.submit(driver!!)
                    }
                }
            } catch (t: Throwable) {
                Log.err("Fatal! Failed to load database", t)
                exitProcess(1)
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

    internal suspend fun abstractQuery(query: Query): Array<Response> {
        if (queue != null) {
            val future = CompletableFuture<Array<Response>>()
            queue!!.add(QueuedMulti(query, future))
            return future.await()
        } else if (driver == null) {
            val future = CompletableFuture<Array<Response>>()
            future.completeExceptionally(UnreachableException("'queue' and 'driver' cannot be null at the same time!"))
            return future.await()
        } else {
            return driver!!.query(query).await()
        }
    }

    internal suspend fun abstractQuerySingle(query: Query): Response {
        if (queue != null) {
            val future = CompletableFuture<Response>()
            queue!!.add(QueuedSingle(query, future))
            return future.await()
        } else if (driver == null) {
            val future = CompletableFuture<Response>()
            future.completeExceptionally(UnreachableException("'queue' and 'driver' cannot be null at the same time!"))
            return future.await()
        } else {
            return driver!!.querySingle(query).await()
        }
    }

    internal fun abstractOnLive(id: String, cb: suspend (LiveResponse) -> kotlin.Unit) {
        if (queue != null) {
            queue!!.add(QueuedLive(id, cb))
        } else if (driver == null) {
            unreachable("'queue' and 'driver' cannot be null at the same time!")
        } else {
            driver!!.onLive(id, cb)
        }
    }

    internal fun abstractOffLive(id: String) {
        if (queue != null) {
            queue!!.remove { it is QueuedLive && it.id == id }
        } else if (driver == null) {
            unreachable("'queue' and 'driver' cannot be null at the same time!")
        } else {
            driver!!.offLive(id)
        }
    }

    @Throws(KeyValidationFailure::class, DisconnectedAccountException::class, MergedAccountException::class,
        SharedAccountException::class, DisabledAccountException::class)
    suspend fun login(uuid: String, usid: String, ip: String, key: PublicKey?, newName: String): PlayerSmallData {
        val keyHash = key?.let { key -> MessageDigest.getInstance("SHA256").digest(key.encoded) }

        kickCache.find { it.key == uuid || it.value.key.contains(keyHash) || it.value.ips.contains(ip)}?.let { entry ->
            if (!entry.value.expires.minus(Clock.System.now()).isPositive()) {
                kickCache.remove(entry.key)

                return@let
            }

            throw KickedAccountException(entry.value.admin, entry.value.reason, entry.value.expires);
        }
        banCache.find { it.key == uuid || it.value.key.contains(keyHash) || it.value.ips.contains(ip)}?.let { entry ->
            if (!entry.value.expires.minus(Clock.System.now()).isPositive()) {
                banCache.remove(entry.key)

                return@let
            }

            throw BannedAccountException(entry.value.admin, entry.value.reason, entry.value.expires, entry.value.server);
        }

        // What's duh SurrealDB smoking?
        val query = run {
            for (x in abstractQuery(Query(DatabaseScripts.loaduserScript)
                .x("uuid", uuid)
                .x("usid", usid)
                .x("ip", ip)
                .x("server", Config.i.serverName)
                .x("key", key?.encoded?.let(Base64.withPadding(Base64.PaddingOption.ABSENT)::encode))
                .x("new_name", newName)).ok()) {
                if (x.result.isNull) continue
                return@run x
            }
            unreachable()
        }

        when (val error = query.result.at("disabled").asInteger()) {
            0 -> {}
            1 -> throw MergedAccountException()
            2 -> throw DisconnectedAccountException()
            3 -> throw KeyValidationFailure()
            4 -> throw DisabledAccountException()
            5 -> throw SharedAccountException()
            8 -> {
                val admin = query.result.at("admin").asString()
                val reason = query.result.at("reason").asString()
                val expires = query.result.at("expires").asLong()
                val server = query.result.at("server").asString()

                val instant = Instant.fromEpochMilliseconds(expires)

                banCache.put(uuid, BannedInfo(Seq.with(ip), if (keyHash != null) Seq.with(keyHash) else Seq.with(), admin, reason, instant, server))
                throw BannedAccountException(admin, reason, instant, server);
            }
            9 -> {
                val admin = query.result.at("admin").asString()
                val reason = query.result.at("reason").asString()
                val expires = query.result.at("expires").asLong()

                val instant = Instant.fromEpochMilliseconds(expires)

                kickCache.put(uuid, KickedInfo(Seq.with(ip), if (keyHash != null) Seq.with(keyHash) else Seq.with(), admin, reason, instant))
                throw KickedAccountException(admin, reason, instant);
            }
            else -> {
                throw RuntimeException("Unexpected login error: $error")
            }
        }

        return PlayerSmallData(
            query.result.at("user_id").asString(),
            query.result.at("id").asString(),
            query.result.at("key_set").asBoolean(),
            if (query.result.at("short_id").isNull) null else query.result.at("short_id").asInteger(),
            query.result.at("permission_level").asInteger(),
        )
    }

    internal fun setPlayerData(player: Player, data: PlayerSmallData) { playerData[player] = data }
    fun localPlayerData(player: Player): PlayerSmallData = playerData[player] ?: throw NullPointerException("Player data must not be null!")
    fun localPlayerDataOrNull(player: Player): PlayerSmallData? = playerData[player]

    suspend fun setKey(player: Player) {
        val data = localPlayerData(player)
        val key = player.publicKey ?: throw IllegalStateException("Cannot set key if there is no key!")
        abstractQuery(Query(DatabaseScripts.setkeyScript)
            .x("id", data.id)
            .x("key", Base64.withPadding(Base64.PaddingOption.ABSENT).encode(key.encoded))).ok()
        data.keySet = true
    }

    // /**
    //  * Kick a player from this server.
    //  */
    // @PublicAPI
    // fun kickPlayer(player: Player, reason: String, duration: Float, admin: Player) {
    //     val playerData = localPlayerData(player)
    //     val adminData = localPlayerData(admin)
    //     Vars.netServer.admins.handleKicked(player.uuid(), player.ip(),
    //         try { duration.times(1000).toLong() } catch (_: Throwable) { Long.MAX_VALUE })
    // }
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

fun Driver.onLive(id: String, cb: suspend (LiveResponse) -> kotlin.Unit): Cancel {
    onLive(id, object : EventCallback<LiveResponse> {
        override fun run(p0: LiveResponse) { Async.run { cb(p0) } }
        override fun fail(p0: Exception) {
            Log.err(p0)
        }
    })

    return Cancel { offLive(id) }
}

fun Array<Response>.ok(): Array<Response> {
    for (response in this) response.ok()
    return this
}