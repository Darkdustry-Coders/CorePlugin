package mindurka.api

import arc.struct.ObjectMap
import arc.util.Log
import mindurka.annotations.PublicAPI
import mindurka.coreplugin.database.Database
import mindurka.coreplugin.database.DatabaseScripts
import mindurka.coreplugin.sessionData
import mindurka.util.getOrPut
import mindurka.util.random
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import net.buj.surreal.Query

/**
 * An object representing a potentially offline player.
 *
 * This object is to be used by command handlers.
 */
@PublicAPI
class OfflinePlayer internal constructor(var lastName: String?, val uuid: String, val usid: String?, val userId: String, val profileId: String) {
    fun sessionData() {
        TODO("Not yet implemented")
    }

    companion object {
        private val cache = ObjectMap<String, OfflinePlayer?>()
        private val playerCache = ObjectMap<Player, OfflinePlayer>()

        /**
         * Remove all (or empty) entries from cache.
         */
        fun clearCache(onlyFailures: Boolean = false) {
            if (onlyFailures) cache.removeAll { it.value == null }
            else cache.clear()
        }

        /**
         * Remove entries from cache that satisfy one of the provided parameters.
         */
        @PublicAPI
        fun invalidateCache(uuid: String?, usid: String?, profileId: String?, userId: String?) {
            cache.removeAll {
                uuid?.let { uuid -> if (uuid == it.value?.uuid) return@removeAll true }
                usid?.let { usid -> if (usid == it.value?.usid) return@removeAll true }
                profileId?.let { id -> if (id == it.value?.profileId) return@removeAll true }
                userId?.let { id -> if (id == it.value?.userId) return@removeAll true }

                return@removeAll false
            }
        }

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
                val obj = playerCache.getOrPut(player) { OfflinePlayer(
                    "",
                    player.con.uuid,
                    player.con.usid,
                    player.sessionData.userId,
                    player.sessionData.profileId,
                ) }
                obj.lastName = player.coloredName()
                return obj
            } catch (_: Exception) {}

            return cache.getOrPut(search) sup@{
                if (cache.size > 128)
                    cache.remove(cache.entries().find { it.value == null }?.key ?: cache.keys().random())

                for (x in cache) {
                    if (x.value?.userId == search) return@sup x.value
                    if (x.value?.profileId?.endsWith(search) == true) return@sup x.value
                    if (checkUuid && x.value?.uuid == search) return@sup x.value
                }

                Groups.player.forEach {
                    if (it.name.contains(search, true))
                        return@sup OfflinePlayer(
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
                            return@sup OfflinePlayer(
                                it.coloredName(),
                                it.uuid(),
                                it.usid(),
                                it.sessionData.userId,
                                it.sessionData.profileId,
                            )
                    }
                }

                val result = Database.abstractQuerySingle(Query(DatabaseScripts.playerFetchScript)
                    .x("search", search).x("check_uuid", checkUuid)).ok()
                return@sup if (result.result.isNull) null
                else OfflinePlayer(
                    result.result.at("name").asString(),
                    result.result.at("uuid").asString(),
                    null,
                    result.result.at("user_id").asString(),
                    result.result.at("profile_id").asString(),
                )
            }
        }
    }

    val player: Player?
        @JvmName("player")
        get() = Groups.player.find { it.uuid() == uuid }
}