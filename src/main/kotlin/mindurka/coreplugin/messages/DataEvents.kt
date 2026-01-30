package mindurka.coreplugin.messages

import kotlinx.serialization.Serializable
import mindurka.annotations.NetworkEvent

@Serializable
@NetworkEvent("mindustry.data.permission-level")
data class PermissionLevel(
    val id: String,
    val level: Int,
)