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