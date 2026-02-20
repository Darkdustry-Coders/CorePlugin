package mindurka.coreplugin

import arc.struct.Seq
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.database.Database
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

    val basename = player.coloredName()
    val player = WeakReference(player)

    var mindurkaCompatVersion = 0
    var publicKey: PublicKey? = null

    var keySet = false
    var permissionLevel = 0
        internal set
    suspend fun setPermissionLevel(level: Int) {
        Database.setPermissionLevel(profileId, level)
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
    var userId: String = defaultString

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
