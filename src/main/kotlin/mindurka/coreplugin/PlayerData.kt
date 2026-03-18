package mindurka.coreplugin

import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.database.Database
import mindustry.Vars
import mindurka.coreplugin.mindurkacompat.Version as MdcVersion
import mindustry.gen.Player
import java.lang.ref.WeakReference
import java.security.PublicKey
import java.util.WeakHashMap

/** Player session data. */
class PlayerData(player: Player) {
    companion object {
        internal const val defaultString = ""
        private val cache = WeakHashMap<Player, PlayerData>()

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

    @JvmField
    val basename = player.coloredName()
    /** A name set with /nick. Overrides [basename]. */
    @JvmField
    var customname: String? = null
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
    /** Full username of a player as displayed in chat and tab. */
    fun fullName() = "$basename [#dadada][[${idString()}]"
    /** Simple username of a player as displayed in kick messages. */
    fun simpleName() = "$basename [#dadada][[${idString()}]"

    fun updateUsername() {
        player.get()?.name = fullName()
    }

    val locks = Seq<RabbitMQ.Lock>(RabbitMQ.Lock::class.java)
    fun releaseLocks() {
        locks.each(RabbitMQ.Lock::release)
        locks.clear()
    }

    suspend fun flush() {}
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
