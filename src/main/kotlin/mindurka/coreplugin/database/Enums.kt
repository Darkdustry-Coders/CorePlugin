package mindurka.coreplugin.database

import mindurka.api.OfflinePlayer
import mindustry.gen.Player
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.Unit as Nada

/**
 * Account disabled codes.
 *
 * See 'resources/doc/account-disabled-codes.md' for documentation.
 */
object DisableCodes {
    /**
     * Account is enabled.
     *
     * System should proceed as normal.
     */
    const val enabled = 0
    /** Account is merged with another account. */
    const val merged = 1
    /** Account was removed from all users and thus is disabled. */
    const val disconnected = 2
    /** Failed to authorize the key, or a key was not provided for a secure account. */
    const val keyValidationFailure = 3
    /** Account was disabled by staff. */
    const val disabled = 4
    /** UUID of the account was leaked, and thus it was banned. */
    const val shared = 5
    /** The IP with which the user has attempted to log in was subnet banned. */
    const val subnetBan = 6
    /** The IP with which the user has attempted to log in was graylisted and thus requires extra verification. */
    const val graylisted = 7
    /**
     * The user was banned on the server.
     *
     * ## Parameters
     * - `id`: The ID of this ban.
     * - `user`: The ID of the user.
     * - `reason`: Reason for why the account is banned.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `admin`: Last username of an admin that issues a ban.
     * - `server`: Server name on which the ban was issued.
     */
    const val banned = 8
    /**
     * The user was kicked from the server. Kicks are server-bound.
     *
     * ## Parameters
     * - `id`: The ID of this kick.
     * - `user`: The ID of the user.
     * - `reason`: Reason for why the account is kicked.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `admin`: Last username of an admin that issues a ban.
     */
    const val kicked = 9
    /** The user is already logged in on this or another server. */
    const val alreadyLoggedIn = 10
    /**
     * The user was kicked from the server. Kicks are server-bound.
     *
     * ## Parameters
     * - `id`: The ID of this kick.
     * - `user`: The ID of the user.
     * - `reason`: Reason for why the account is kicked.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `initiator`: Last username of a player that started the votekick.
     * - `votes`: Last usernames of players that voted in favor of the kick.
     */
    const val votekicked = 11
    /**
     * The user was automatically blacklisted by the system.
     *
     * The server should not accept messages from that IP for a while.
     */
    const val blacklisted = 12
}

object PermLevels {
    /**
     * Default privileges.
     */
    const val default = 0
    /**
     * /mute, /kick, /ban, etc.
     */
    const val moderator = 100
    /**
     * Map editing, /artv, tracing.
     */
    const val admin = 200
    /**
     * For now no extra perms.
     */
    const val admin_overseer = 300
    /**
     * /js, /sql.
     */
    const val console = 1000
}

// class OffenseStat(
//     /** The amount of times the player was banned for the same reason. Does not include pardoned bans. */
//     val times: Long,
//     /** The player that is receiving the punishment. */
//     val player: OfflinePlayer,
//     val duration: Duration,
// )

// /**
//  * Categorized offense kinds.
//  */
// enum class OffenseKind(
//     @JvmField val ruleOrdinal: Long,
//     @JvmField val legacyReason: String,
//     private val punish: (OffenseStat) -> Nada) {
//     Cheating(1, "Cheating"),
//     Grief(2, "Grief"),
//
//     ;
//
//     @JvmField val tlKey: String = run {
//         name.replace(Regex("[A-Z]")) { "${if (it.range.contains(0)) "" else "-"}${it.value.lowercase()}" }
//     }
//
//     companion object {
//         @JvmField val items = entries
//         @JvmStatic fun of(value: Long): OffenseKind? = items.find { it.ruleOrdinal == value }
//     }
// }