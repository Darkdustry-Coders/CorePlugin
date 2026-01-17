package mindurka.coreplugin.database

import mindurka.annotations.Autosnowflake
import mindurka.annotations.DatabaseEntry
import mindurka.annotations.Unique

@DatabaseEntry("players.master")
data class PlayerMasterInfo(
    @Autosnowflake
    val id: Int,
)

enum class MindustryCypherType {
    RSA,
    Ed25519,
}

@DatabaseEntry("players.mindustry")
data class MindustryPlayerInfo(
    @Autosnowflake
    val id: Int,
    var masterId: Int,

    @Unique
    var uuid: String,
    @Unique
    var pubKey: ByteArray? = null,
    /** sha256 hash of pubKey. */
    @Unique
    var pubKeyHash: ByteArray? = null,
    var cypherType: MindustryCypherType = MindustryCypherType.RSA,

    /** Play time, in seconds. */
    var playtime: Int = 0,
    var waves: Int = 0,
    var gamesPlayed: Int = 0,
    var blocksPlaced: Int = 0,
    var blocksBroken: Int = 0,
    /** Currently equipped rank. */
    var rank: String? = null,
) {
    // Our glorious leader IntelliJ IDEA wanted it.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MindustryPlayerInfo

        if (id != other.id) return false
        if (masterId != other.masterId) return false
        if (playtime != other.playtime) return false
        if (waves != other.waves) return false
        if (gamesPlayed != other.gamesPlayed) return false
        if (blocksPlaced != other.blocksPlaced) return false
        if (blocksBroken != other.blocksBroken) return false
        if (uuid != other.uuid) return false
        if (!pubKey.contentEquals(other.pubKey)) return false
        if (!pubKeyHash.contentEquals(other.pubKeyHash)) return false
        if (cypherType != other.cypherType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + masterId
        result = 31 * result + playtime
        result = 31 * result + waves
        result = 31 * result + gamesPlayed
        result = 31 * result + blocksPlaced
        result = 31 * result + blocksBroken
        result = 31 * result + uuid.hashCode()
        result = 31 * result + (pubKey?.contentHashCode() ?: 0)
        result = 31 * result + (pubKeyHash?.contentHashCode() ?: 0)
        result = 31 * result + cypherType.hashCode()
        return result
    }
}

@DatabaseEntry("players.mindustry.gamemodeInfo")
data class MindustryPlayerGamemodeInfo(
    val id: Int,
    val gamemode: String,

    var wins: Int = 0,
    var ovas: Int = 0,
    var rank: Int = 0,
)

@DatabaseEntry("players.mindustry.rank")
data class MindustryPlayerRank(
    val id: Int,
    val rank: String,
)

@DatabaseEntry("players.rank")
data class MindustryRank(
    val rank: String,
    var prefix: String?,
)
