package mindurka.coreplugin.database

import arc.struct.ObjectMap
import mindurka.util.Async
import mindurka.util.encodeURIComponent
import mindurka.util.random
import mjson.Json
import net.buj.surreal.Query

data class IspTables(
    val isp: String,
    val ass: String,
    val asname: String,
    val proxy: Boolean,
    val hosting: Boolean,
    val mobile: Boolean,
) {
    companion object {
        private val map = ObjectMap<String, IspTables?>(128)

        suspend fun of(address: String): IspTables? {
            map[address]?.let { return it }

            val resp = Database.abstractQuerySingle(Query(DatabaseScripts.ispsFetchScript).x("ip", address)).ok()
            if (!resp.result.isNull) {
                val tables = IspTables(
                    resp.result.at("isp").asString(),
                    resp.result.at("as").asString(),
                    resp.result.at("as_name").asString(),
                    resp.result.at("proxy").asBoolean(),
                    resp.result.at("hosting").asBoolean(),
                    resp.result.at("mobile").asBoolean(),
                )
                if (map.size > 100) map.remove(map.keys().random())
                map.put(address, tables)
                return tables
            }

            val http = try {
                Json.read(Async.fetchHttpString("https://ip-api.com/json/${encodeURIComponent(address)}?fields=21229056"))
            } catch (_: Throwable) {
                return null
            }

            if (!http.at("status").isString) return null
            if (http.at("status").asString() != "success") return null

            val tables = IspTables(
                resp.result.at("isp").asString(),
                resp.result.at("as").asString(),
                resp.result.at("asname").asString(),
                resp.result.at("proxy").asBoolean(),
                resp.result.at("hosting").asBoolean(),
                resp.result.at("mobile").asBoolean(),
            )

            Database.abstractQuerySingle(Query(DatabaseScripts.ispsUpdateScript)
                .x("ip", address).x("isp", tables.isp).x("as", tables.ass)
                .x("as_name", tables.asname).x("proxy", tables.proxy)
                .x("hosting", tables.hosting).x("mobile", tables.mobile)).ok()

            if (map.size > 100) map.remove(map.keys().random())
            map.put(address, tables)
            return tables
        }
    }
}