package mindurka.coreplugin.messages

import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent

@Serializable
@NetworkEvent("discord.chatIntegration.mindustry", ttl = 5)
data class DiscordIntegrationMessage (
    /** Id of the player. 'null' for console. */
    val playerId: Int?,
    /** Current username of the player. */
    val playerUsername: String,
    /** Sent message. */
    val message: String,
    /** Team chat this message was sent in. */
    val team: Int?,
)
