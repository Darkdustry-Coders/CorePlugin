package mindurka.coreplugin.messages

import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent
import mindurka.annotations.PublicAPI

/**
 * Server info event.
 *
 * Used to announce that a server is online.
 */
@PublicAPI
@Serializable
@NetworkEvent("generic.serverInfo", ttl = 5)
data class ServerInfo(
    /**
     * Server's name.
     *
     * Always displayed under the portal.
     */
    val name: String,
    /**
     * Server's description.
     *
     * Shown up close to the portal. Should contain a brief explanation
     * of the gamemode.
     */
    val motd: String,
    /**
     * Server's gamemode.
     *
     * Mapped to {generic.gamemode.<gamemode>} language key.
     */
    val gamemode: String,
    /** Current map. */
    val map: String,
    /** Player count. */
    val players: Int,
    /**
     * Max player count.
     *
     * -1 if infinite.
     */
    val maxPlayers: Int,
    /**
     * Wave count.
     *
     * -1 if none.
     */
    val wave: Int,
    /**
     * Max wave count.
     *
     * -1 if no limit.
     */
    val maxWaves: Int,
    /**
     * Server IP.
     *
     * This includes both domain and port separated with a semicolon (`:`).
     */
    val ip: String,
)

/**
 * Server down event.
 *
 * Sent when a server goes offline.
 *
 * This event may not get sent, so don't rely on it!
 */
@Serializable
@NetworkEvent("generic.serverDown", ttl = 5)
class ServerDown
