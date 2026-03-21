package mindurka.coreplugin

import arc.struct.Seq
import arc.util.Log
import mindurka.annotations.PublicAPI
import mindurka.api.interval
import mindurka.coreplugin.database.Database
import mindurka.util.Async
import mindurka.util.newSeq
import mindustry.Vars
import mindustry.gen.KickCallPacket
import mindustry.gen.KickCallPacket2
import mindurka.coreplugin.mindurkacompat.Version as MdcVersion
import mindustry.gen.Player
import mindustry.net.Packets
import mjson.Json
import net.buj.surreal.Query
import java.lang.ref.WeakReference
import java.security.PublicKey
import java.util.WeakHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Player session data. */
class PlayerData private constructor(player: Player) {
    companion object {
        internal const val defaultString = ""
        private val cache = WeakHashMap<Player, PlayerData>()
        private class GcEntry(@JvmField val player: WeakReference<Player>, @JvmField val data: PlayerData)
        private val gc = newSeq<GcEntry>(ordered = false)

        init {
            val removed = newSeq<GcEntry>(ordered = false)
            interval(15f) {
                gc.removeAll {
                    if (it.player.get() == null) {
                        if (it.data.exitHandledCorrectly) true
                        else {
                            removed.add(it)
                            true
                        }
                    } else false
                }
                if (!removed.isEmpty) {
                    val entries = removed.copy()
                    Log.err("${entries.size} entr${if (entries.size == 1) "y" else "ies"} were not handled correctly!")
                    removed.clear()
                    Async.run {
                        for (entry in entries) {
                            entry.data.releaseLocks()
                        }
                    }
                }
            }
        }

        /**
         * Obtain session data for player.
         *
         * If none exists, new data will be created.
         */
        @PublicAPI
        @JvmStatic
        fun of(player: Player): PlayerData = cache.getOrPut(player) { PlayerData(player) }

        /**
         * Obtain session data for player.
         *
         * If none exists, returns `null`.
         */
        @PublicAPI
        @JvmStatic
        fun ofOrNull(player: Player): PlayerData? = cache[player]
    }

    init {
        gc.add(GcEntry(WeakReference(player), this))
    }

    private var exitHandledCorrectly = false

    @JvmField
    val basename = player.coloredName()
    /** A name set with /nick. Overrides [basename]. */
    var customname: String? = null
        set(value) {
            field = value
            updateUsername()
        }
    @JvmField
    val player = WeakReference(player)

    @JvmField
    var mindurkaCompatVersion = 0
    @JvmField
    var publicKey: PublicKey? = null

    @JvmField
    var keySet = false

    var permissionLevel = 0
        private set
    suspend fun setPermissionLevel(level: Int) {
        Database.setPermissionLevel(profileId, level)
        permissionLevel = level
        if (keySet && level >= 100) Vars.netServer.admins.adminPlayer(uuid, usid)
        else Vars.netServer.admins.unAdminPlayer(uuid)
        player.get()?.admin = level >= 100
    }
    /**
     * Set permission level without updating the database.
     */
    internal fun `unsafe$rawSetPermissionLevel`(level: Int) {
        permissionLevel = level
    }

    var shortId: Long? = null
        set(value) {
            field = value
            updateUsername()
        }
    var profileId: String = defaultString
        set(value) {
            field = value
            updateUsername()
        }
    @JvmField
    var userId: String = defaultString

    val usid = player.usid()
    val uuid = player.uuid()

    fun idString() = shortId?.toString() ?: profileId.takeLast(6)
    /** Just the name. */
    val name: String get() = customname ?: basename
    /** Full username of a player as displayed in chat and tab. */
    fun fullName() = "$name [#dadada][[${idString()}]"
    /** Simple username of a player as displayed in kick messages. */
    fun simpleName() = "$name [#dadada][[${idString()}]"

    fun updateUsername() {
        player.get()?.name = fullName()
    }

    val locks = Seq<RabbitMQLock>(RabbitMQLock::class.java)
    suspend fun releaseLocks() {
        for (lock in locks) lock.release()
        locks.clear()
    }

    internal fun handleUpdateOutput(out: Json) {
        var updateUsername = false

        if (out.has("set_short_id")) {
            shortId = out.at("set_short_id").asLong()
            updateUsername = true
        }

        if (updateUsername) updateUsername()
    }

    private var lastPushed = TimeSource.Monotonic.markNow()
    suspend fun flush() {
        val now = TimeSource.Monotonic.markNow()
        val elapsed = now - lastPushed
        lastPushed = now

        val query = StringBuilder($$"update only type::record(\"mindustry_profile\", <uuid> $profile) set ")
        query.append("total_play_time += duration::from_millis(${elapsed.inWholeMilliseconds})")
        query.append(", play_time += duration::from_millis(${elapsed.inWholeMilliseconds})")
        query.append(";\n")
        query.append($$"return fn::mindustry_update_profile(type::record(\"mindustry_profile\", <uuid> $profile));")

        handleUpdateOutput(Database.abstractQuerySingle(Query(query.toString())
            .x("profile", profileId)).ok().result)
    }

    /**
     * Handle player existing.
     *
     * This MUST be called.
     */
    suspend fun playerLeft(player: Player) {
        exitHandledCorrectly = true
        releaseLocks()
    }

    suspend fun kickSilent(player: Player, reason: String) {
        val packet = KickCallPacket()
        packet.reason = reason
        player.con.send(packet, true)
        player.con.close()
        playerLeft(player)
    }

    suspend fun kick(player: Player, reason: String) {
        if (player.isAdded) player.kick(reason)
        else player.con.kick(reason)
        playerLeft(player)
    }

    suspend fun kickSilent(player: Player, reason: Packets.KickReason) {
        val packet = KickCallPacket2()
        packet.reason = reason
        player.con.send(packet, true)
        player.con.close()
        playerLeft(player)
    }

    suspend fun kick(player: Player, reason: Packets.KickReason) {
        if (player.isAdded) player.kick(reason)
        else player.con.kick(reason)
        playerLeft(player)
    }
}

/**
 * Get player data.
 */
@PublicAPI val Player.sessionData get() = PlayerData.of(this)
/**
 * Check if player has MindurkaCompat installed.
 */
@PublicAPI val Player.hasMindurkaCompat get() = sessionData.mindurkaCompatVersion != 0
/**
 * Obtain raw number for MindurkaCompat version.
 */
@PublicAPI val Player.mindurkaCompatVersion get() = sessionData.mindurkaCompatVersion
/**
 * Obtain MindurkaCompat version.
 */
@PublicAPI val Player.mindurkaCompat get() = MdcVersion.of(sessionData.mindurkaCompatVersion)
@PublicAPI suspend fun Player.ckick(reason: String) = PlayerData.of(this).kick(this, reason)
@PublicAPI suspend fun Player.cskick(reason: String) = PlayerData.of(this).kickSilent(this, reason)
@PublicAPI suspend fun Player.ckick(reason: Packets.KickReason) = PlayerData.of(this).kick(this, reason)
@PublicAPI suspend fun Player.cskick(reason: Packets.KickReason) = PlayerData.of(this).kickSilent(this, reason)
