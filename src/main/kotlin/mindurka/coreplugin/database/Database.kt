package mindurka.coreplugin.database

import arc.Core
import arc.struct.ByteSeq
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import arc.util.io.Streams
import buj.tl.Tl
import kotlinx.coroutines.future.await
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mindurka.annotations.PublicAPI
import mindurka.api.Cancel
import mindurka.api.OfflinePlayer
import mindurka.api.sleep
import mindurka.config.SharedConfig
import mindurka.coreplugin.Config
import mindurka.coreplugin.PlayerData
import mindurka.coreplugin.sessionData
import mindurka.util.Async
import mindurka.util.UnreachableException
import mindurka.util.collect
import mindurka.util.durationToTlString
import mindurka.util.map
import mindurka.util.random
import mindurka.util.unreachable
import mindustry.Vars
import mindustry.gen.Player
import mindustry.net.NetConnection
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
import java.util.concurrent.CompletableFuture
import kotlin.io.encoding.Base64
import kotlin.jvm.javaClass
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
class BannedAccountException(
    val banId: String,
    val admin: String,
    val reason: String,
    val expires: Instant?,
    val server: String?
): Exception("Unhandled ban")
class KickedAccountException(
    val kickId: String,
    val admin: String,
    val reason: String,
    val expires: Instant?
): Exception("Unhandled kick")
class GraylistedAccountException: Exception("Unhandled graylist")
class AnotherLocationException: Exception("Unhandled double login")
class VotekickedAccountException(val votekickId: String, val reason: String, val expires: Instant, val initiator: String, val votes: Seq<String>): Exception("Unhandled votekick")

data class BannedInfo(
    val id: String,
    val user: String,
    val ips: Seq<String>,
    val key: Seq<ByteArray>,
    val admin: String,
    val reason: String,
    val expires: Instant?,
    val server: String
)

data class KickedInfo(
    val id: String,
    val user: String,
    val ips: Seq<String>,
    val key: Seq<ByteArray>,
    val admin: String,
    val reason: String,
    val expires: Instant?,
)

data class VotekickedInfo(
    val id: String,
    val user: String,
    val ips: Seq<String>,
    val key: Seq<ByteArray>,
    val reason: String,
    val expires: Instant,
    val initiator: String,
    val votes: Seq<String>,
)

internal object DatabaseScripts {
    val initScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/init.surrealql"))
    val liveScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/live.surrealql"))
    val loaduserScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/loaduser.surrealql"))
    val setkeyScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/setkey.surrealql"))
    val setpermissionlevelScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/setpermissionlevel.surrealql"))

    val ispsFetchScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/isps_fetch.surrealql"))
    val ispsUpdateScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/isps_update.surrealql"))

    val playerFetchScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/player_fetch.surrealql"))

    val votekickScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/votekick.surrealql"))
    val kickScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/kick.surrealql"))
    val pardonScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/pardon.surrealql"))
    val banScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/ban.surrealql"))
    val unbanScript: String = Streams.copyString(javaClass.classLoader.getResourceAsStream("sql/unban.surrealql"))
}

object Database {
    val banCache = ObjectMap<String, BannedInfo>()
    val kickCache = ObjectMap<String, KickedInfo>()
    val votekickCache = ObjectMap<String, VotekickedInfo>()

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

                val liveQueries = driver!!.query(Query(DatabaseScripts.liveScript)
                    .x("server_name", Config.i.serverName)).await().ok()

                driver!!.onLive(liveQueries[0].result.asString()) { update ->
                    val votekickId = update.data.at("id").asString()
                    votekickCache.removeAll { it.value.id == votekickId }
                }
                driver!!.onLive(liveQueries[1].result.asString()) { update ->
                    val kickId = update.data.at("id").asString()
                    kickCache.removeAll { it.value.id == kickId }
                }
                driver!!.onLive(liveQueries[2].result.asString()) { update ->
                    val banId = update.data.at("id").asString()
                    banCache.removeAll { it.value.id == banId }
                }

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
    suspend fun login(uuid: String, usid: String, ip: String, key: PublicKey?, newName: String, session: PlayerData) {
        val keyHash = key?.let { key -> MessageDigest.getInstance("SHA256").digest(key.encoded) }

        votekickCache.find { it.key == uuid || it.value.key.contains(keyHash) || it.value.ips.contains(ip)}?.let { entry ->
            if (!entry.value.expires.minus(Clock.System.now()).isPositive()) {
                votekickCache.remove(entry.key)

                return@let
            }

            throw VotekickedAccountException(entry.value.reason, entry.value.id, entry.value.expires, entry.value.initiator, entry.value.votes);
        }
        kickCache.find { it.key == uuid || it.value.key.contains(keyHash) || it.value.ips.contains(ip)}?.let { entry ->
            if (entry.value.expires?.minus(Clock.System.now())?.isPositive() == false) {
                kickCache.remove(entry.key)

                return@let
            }

            throw KickedAccountException(entry.value.id, entry.value.admin, entry.value.reason, entry.value.expires);
        }
        banCache.find { it.key == uuid || it.value.key.contains(keyHash) || it.value.ips.contains(ip)}?.let { entry ->
            if (entry.value.expires?.minus(Clock.System.now())?.isPositive() == false) {
                banCache.remove(entry.key)

                return@let
            }

            throw BannedAccountException(entry.value.id, entry.value.admin, entry.value.reason, entry.value.expires, entry.value.server);
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
            DisableCodes.enabled -> {}
            DisableCodes.merged -> throw MergedAccountException()
            DisableCodes.disconnected -> throw DisconnectedAccountException()
            DisableCodes.keyValidationFailure -> throw KeyValidationFailure()
            DisableCodes.disabled -> throw DisabledAccountException()
            DisableCodes.shared -> throw SharedAccountException()
            DisableCodes.banned -> {
                val id = query.result.at("id").asString()
                val user = query.result.at("user").asString()
                val admin = query.result.at("admin").asString()
                val reason = query.result.at("reason").asString()
                val expires = if (query.result.at("expires").isNull) null else Instant.fromEpochMilliseconds(query.result.at("expires").asLong())
                val server = query.result.at("server").asString()

                if (banCache.size > 128) votekickCache.remove(votekickCache.keys().random())
                banCache.put(uuid, BannedInfo(id, user, Seq.with(ip), if (keyHash != null) Seq.with(keyHash) else Seq.with(), admin, reason, expires, server))
                throw BannedAccountException(id, admin, reason, expires, server);
            }
            DisableCodes.kicked -> {
                val id = query.result.at("id").asString()
                val user = query.result.at("user").asString()
                val admin = query.result.at("admin").asString()
                val reason = query.result.at("reason").asString()
                val expires = if (query.result.at("expires").isNull) null else Instant.fromEpochMilliseconds(query.result.at("expires").asLong())

                if (kickCache.size > 128) votekickCache.remove(votekickCache.keys().random())
                kickCache.put(uuid, KickedInfo(id, user, Seq.with(ip), if (keyHash != null) Seq.with(keyHash) else Seq.with(), admin, reason, expires))
                throw KickedAccountException(id, admin, reason, expires);
            }
            DisableCodes.votekicked -> {
                val id = query.result.at("id").asString()
                val initiator = query.result.at("initiator").asString()
                val votes = query.result.at("votes").asList().iterator().map {
                    if (it !is String) unreachable("'votes' is not a string list")
                    it
                }.collect(Seq())
                val reason = query.result.at("reason").asString()
                val expires = Instant.fromEpochMilliseconds(query.result.at("expires").asLong())
                val userId = query.result.at("user").asString()

                if (votekickCache.size > 128) votekickCache.remove(votekickCache.keys().random())
                votekickCache.put(uuid, VotekickedInfo(id, userId, Seq.with(ip), if (keyHash != null) Seq.with(keyHash) else Seq.with(), reason, expires, initiator, votes))
                throw VotekickedAccountException(id, reason, expires, initiator, votes);
            }
            else -> {
                throw RuntimeException("Unexpected login error: $error")
            }
        }

        session.userId = query.result.at("user_id").asString()
        session.profileId = query.result.at("id").asString()
        session.keySet = query.result.at("key_set").asBoolean()
        session.shortId = if (query.result.at("short_id").isNull) null else query.result.at("short_id").asLong()
        session.`unsafe$rawSetPermissionLevel`(query.result.at("permission_level").asInteger())
    }

    internal suspend fun setPermissionLevel(profileId: String, level: Int) {
        abstractQuery(Query(DatabaseScripts.setpermissionlevelScript)
            .x("permissionLevel", level)
            .x("id", profileId)).ok()
    }

    suspend fun setKey(player: Player) {
        val data = player.sessionData
        val key = data.publicKey ?: throw IllegalStateException("Cannot set key if there is no key!")
        abstractQuery(Query(DatabaseScripts.setkeyScript)
            .x("id", data.profileId)
            .x("key", Base64.withPadding(Base64.PaddingOption.ABSENT).encode(key.encoded))).ok()
        data.keySet = true
    }

    internal fun votekickConnection(con: NetConnection, votekickId: String, locale: String, reason: String, expires: Instant, initiator: String, votes: Seq<String>) {
        val votesS = run {
            val builder = StringBuilder(run {
                var i = 0
                for (x in votes) {
                    if (i != 0) i++
                    i += x.length
                }
                i
            })
            for (x in votes) {
                if (!builder.isEmpty()) builder.append("\n")
                builder.append(x)
            }
            builder
        }

        val remaining = durationToTlString(max((expires - Clock.System.now()).inWholeMilliseconds, 0) / 1000f)

        con.kick(Tl.fmt(locale)
            .put("id", votekickId)
            .put("initiator", initiator)
            .put("votes", votesS.toString())
            .put("remaining", remaining)
            .put("reason", reason)
            .done("{generic.kick.votekick}"))
    }

    internal suspend fun votekick(player: Player, initiator: Player, votes: Seq<Player>, reason: String) {
        val id = abstractQuerySingle(Query(DatabaseScripts.votekickScript)
            .x("user", player.sessionData.userId)
            .x("initiator", player.sessionData.userId)
            .x("votes", votes.iterator().map { it.sessionData.userId }.collect(ArrayList()))
            .x("ip", player.con.address)
            .x("reason", reason)
            .x("server", Config.i.serverName)).ok().result.at("id").asString()
        votekickConnection(player.con, id, player.locale, reason, Clock.System.now() + 30.minutes,
            initiator.sessionData.simpleName(), votes.map { it.sessionData.simpleName() })
    }

    /**
     * Remove all kicks from a player.
     */
    @PublicAPI
    suspend fun pardon(userId: String): Boolean {
        votekickCache.removeAll { it.value.user == userId }
        kickCache.removeAll { it.value.user == userId }
        return abstractQuerySingle(Query(DatabaseScripts.pardonScript)
            .x("user", userId)
            .x("server", Config.i.serverName)).ok().result.asBoolean()
    }

    internal fun kickConnection(con: NetConnection, kickId: String, locale: String, reason: String, expires: Instant?, admin: String) {
        val remaining = durationToTlString(expires?.let { max((it - Clock.System.now()).inWholeMilliseconds, 0) / 1000f } ?: Float.POSITIVE_INFINITY)

        con.kick(Tl.fmt(locale)
            .put("id", kickId)
            .put("admin", admin)
            .put("remaining", remaining)
            .put("reason", reason)
            .done("{generic.kick.kick}"))
    }

    /**
     * Kick a player from this server.
     */
    @PublicAPI
    suspend fun kick(player: Player, admin: Player?, duration: Duration?, reason: String) {
        val id = abstractQuerySingle(Query(DatabaseScripts.kickScript)
            .x("user", player.sessionData.userId)
            .x("admin", admin?.sessionData?.userId)
            .x("ip", player.con.address)
            .x("duration", duration?.let { it.inWholeMilliseconds / 1000f })
            .x("reason", reason)
            .x("server", Config.i.serverName)).ok().result.at("id").asString()
        kickConnection(player.con, id, player.locale, reason,
            duration?.let { Clock.System.now() + it }, admin?.sessionData?.simpleName() ?: "<Console>")
    }
    /**
     * Kick a player from this server.
     */
    @PublicAPI
    suspend fun kick(player: OfflinePlayer, admin: Player?, duration: Duration?, reason: String) {
        val id = abstractQuerySingle(Query(DatabaseScripts.kickScript)
            .x("user", player.userId)
            .x("admin", admin?.sessionData?.userId)
            .x("ip", player.player?.con?.address)
            .x("duration", duration?.let { it.inWholeMilliseconds / 1000f })
            .x("reason", reason)
            .x("server", Config.i.serverName)).ok().result.at("id").asString()
        player.player?.let { po ->
            kickConnection(po.con, id, po.locale, reason,
                duration?.let { Clock.System.now() + it }, admin?.sessionData?.simpleName() ?: "<Console>")
        }
    }

    internal fun banConnection(con: NetConnection, banId: String, locale: String, reason: String, expires: Instant?, admin: String) {
        val remaining = durationToTlString(expires?.let { max((it - Clock.System.now()).inWholeMilliseconds, 0) / 1000f } ?: Float.POSITIVE_INFINITY)

        con.kick(Tl.fmt(locale)
            .put("id", banId)
            .put("admin", admin)
            .put("remaining", remaining)
            .put("reason", reason)
            .done("{generic.kick.ban}"))
    }

    /**
     * Ban a player from this server.
     */
    @PublicAPI
    suspend fun ban(player: Player, admin: Player?, duration: Duration?, reason: String) {
        val id = abstractQuerySingle(Query(DatabaseScripts.banScript)
            .x("user", player.sessionData.userId)
            .x("admin", admin?.sessionData?.userId)
            .x("ip", player.con.address)
            .x("duration", duration?.let { it.inWholeMilliseconds / 1000f })
            .x("reason", reason)
            .x("server", Config.i.serverName)).ok().result.at("id").asString()
        banConnection(player.con, id, player.locale, reason,
            duration?.let { Clock.System.now() + it }, admin?.sessionData?.simpleName() ?: "<Console>")
    }
    /**
     * Ban a player from this server.
     */
    @PublicAPI
    suspend fun ban(player: OfflinePlayer, admin: Player?, duration: Duration?, reason: String) {
        val id = abstractQuerySingle(Query(DatabaseScripts.banScript)
            .x("user", player.userId)
            .x("admin", admin?.sessionData?.userId)
            .x("ip", player.player?.con?.address)
            .x("duration", duration?.let { it.inWholeMilliseconds / 1000f })
            .x("reason", reason)
            .x("server", Config.i.serverName)).ok().result.at("id").asString()
        player.player?.let { po ->
            banConnection(po.con, id, po.locale, reason,
                duration?.let { Clock.System.now() + it }, admin?.sessionData?.simpleName() ?: "<Console>")
        }
    }

    /**
     * Remove all bans from a player.
     */
    @PublicAPI
    suspend fun unban(userId: String): Boolean {
        banCache.removeAll { it.value.user == userId }
        return abstractQuerySingle(Query(DatabaseScripts.unbanScript)
            .x("user", userId)).ok().result.asBoolean()
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