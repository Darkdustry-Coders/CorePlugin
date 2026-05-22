package mindurka.coreplugin.messages

import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent

/**
 * Ban an IP on host level.
 *
 * Blacklisted entries should be automatically cleaned up over time.
 */
@Serializable
@NetworkEvent("control.firewall.ban")
data class AddFirewallBan(
    val ip: String,
)

/**
 * Request the server to bring the player back once the server is back online.
 *
 * The request will only be fulfilled if the player does not disconnect from
 * the server and will connect to it quickly enough.
 */
@Serializable
@NetworkEvent("mindustry.control.bringback")
data class BringPlayerBack(
    /** Profile IDs of player to return. */
    val profileIDs: List<String>,
)
