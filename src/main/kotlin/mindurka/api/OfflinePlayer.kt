package mindurka.api

import arc.util.Log
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.DatabaseScripts
import mindurka.coreplugin.database.ok
import mindurka.coreplugin.sessionData
import mindustry.gen.Groups
import mindustry.gen.Player
import net.buj.surreal.Query

/**
 * An object representing a potentially offline player.
 *
 * This object is to be used by command handlers.
 */
@PublicAPI
class OfflinePlayer internal constructor(var lastName: String, val uuid: String, val usid: String?, val userId: String, val profileId: String) {
    fun sessionData() {
        TODO("Not yet implemented")
    }

    companion object {
        /**
         * Resolve an offline player.
         *
         * This will make a database request if player is not online.
         */
        @PublicAPI
        @JvmStatic
        @JvmOverloads
        suspend fun resolve(search: String, checkUuid: Boolean = false): OfflinePlayer? {
            if (search.startsWith('#')) try {
                val id = search.substring(1).toInt()
                val player = Groups.player.find { it.id == id } ?: return null
                return OfflinePlayer(
                    player.coloredName(),
                    player.con.uuid,
                    player.con.usid,
                    player.sessionData.userId,
                    player.sessionData.profileId,
                )
            } catch (_: Exception) {}

            Groups.player.forEach {
                if (it.name.contains(search, true)
                    || checkUuid && it.uuid() == search
                    || checkUuid && it.usid() == search)
                    return OfflinePlayer(
                        it.coloredName(),
                        it.uuid(),
                        it.usid(),
                        it.sessionData.userId,
                        it.sessionData.profileId,
                    )
            }

            if (checkUuid) {
                Groups.player.forEach {
                    if (it.uuid() == search || it.usid() == search)
                        return OfflinePlayer(
                            it.coloredName(),
                            it.uuid(),
                            it.usid(),
                            it.sessionData.userId,
                            it.sessionData.profileId,
                        )
                }
            }

            val result = Database.abstractQuery(Query(DatabaseScripts.playerFetchScript)
                .x("search", search).x("check_uuid", checkUuid)).ok().last()
            return if (result.result.isNull) null
            else OfflinePlayer(
                result.result.at("name").asString(),
                result.result.at("uuid").asString(),
                null,
                result.result.at("user_id").asString(),
                result.result.at("profile_id").asString(),
            )
        }

        @JvmStatic
        fun of(player: Player) = OfflinePlayer(
            player.coloredName(),
            player.uuid(),
            player.usid(),
            player.sessionData.userId,
            player.sessionData.profileId,
        )
    }

    val player: Player?
        @JvmName("player")
        get() = Groups.player.find { it.uuid() == uuid }
}