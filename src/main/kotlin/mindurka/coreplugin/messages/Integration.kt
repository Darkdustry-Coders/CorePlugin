package mindurka.coreplugin.messages

import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent

@Serializable
@NetworkEvent("generic.message", ttl = 5)
data class ServerMessage (
    /** Plaintext message. */
    val message: String,
    /** Address of the server. */
    val service: String,
    /** Shared user ID. */
    val user: Int?,
    /** Displayed username. */
    val username: String?,
    /** Displayed avatar. */
    val avatarUrl: String?,
)
