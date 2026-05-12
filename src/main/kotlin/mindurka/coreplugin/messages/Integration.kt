package mindurka.coreplugin.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent

@Serializable
sealed class FileKind {
    /// Kind is not known.
    @Serializable
    @SerialName("unknown")
    object Unknown: FileKind()

    /// An image.
    @Serializable
    @SerialName("image")
    data class Image(val width: Int, val height: Int): FileKind()

    /// Audio.
    @Serializable
    @SerialName("audio")
    object Audio: FileKind()

    /// Video.
    @Serializable
    @SerialName("video")
    data class Video(
        val width: Int,
        val height: Int,
        /** Duration in seconds. */
        val duration: Float): FileKind()
}

@Serializable
data class AttachedFile (
    /**
     * A URL to download the file.
     *
     * Either the direct link, or using the buffer transfer service on the website. The link is only
     * guaranteed to work for a very limited time, hopefully enough for a client to start downloading it
     * in a few seconds after the link has been created.
     */
    val url: String,
    /**
     * File size, in bytes.
     *
     * Reported by the service, the actual file may differ in size.
     */
    val size: Int?,
    /**
     * Extra information about the file.
     *
     * Some services may ignore all files that are not i.e. an image.
     */
    val kind: FileKind,
)

@Serializable
@NetworkEvent("generic.message", ttl = 5)
data class ServerMessage (
    /** Plaintext message. */
    val message: String,
    /**
     * Embeda service address.
     *
     * Service address must be formatted as `[<subuser>/]<user>[+<tag>]@<service>[/<subservice>]`. Tag is usually
     * `+bot` for bots and `+system` for console messages.
     *
     * `service` for Mindustry messages will always be set to `mindustry`.
     */
    val service: String,
    /** Shared user ID. */
    val user: String? = null,
    /**
     * Displayed username.
     *
     * May be `null` in case of a system message.
     */
    val username: String? = null,
    /** Displayed avatar. */
    val avatarUrl: String? = null,
    /**
     * Attached files.
     *
     * Nullable for compatibility.
     */
    val files: List<AttachedFile>? = null,
)

@Serializable
@NetworkEvent("mindustry.player-join", ttl = 5)
data class PlayerJoined (
    /** Shared user ID. */
    val user: String,
    /** Displayed username. */
    val username: String,
)

@Serializable
@NetworkEvent("mindustry.player-left", ttl = 5)
data class PlayerLeft (
    /** Shared user ID. */
    val user: String,
    /** Displayed username. */
    val username: String,
)
