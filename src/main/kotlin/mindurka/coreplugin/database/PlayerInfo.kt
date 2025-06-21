package mindurka.coreplugin.database

import mindurka.annotations.DatabaseEntry
import mindurka.annotations.PrimaryKey

@DatabaseEntry("players")
data class PlayerInfo(
    @PrimaryKey
    val id: Int,
    val uuid: String,
)
