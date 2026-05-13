package mindurka.coreplugin.database

import arc.struct.ObjectMap
import arc.util.Log
import mindurka.annotations.PublicAPI
import mindurka.util.Async
import mindurka.util.encodeURIComponent
import mindurka.util.random
import mjson.Json
import net.buj.surreal.Query

@PublicAPI
data class IspTables(
    @JvmField
    val isp: String,
    @JvmField
    val ass: String,
    @JvmField
    val asname: String,
    @JvmField
    val proxy: Boolean,
    @JvmField
    val hosting: Boolean,
    @JvmField
    val mobile: Boolean,
) {
    companion object {
        private val map = ObjectMap<String, IspTables?>(128)

        suspend fun of(address: String): IspTables? {
            if (map.containsKey(address)) return map[address]

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
                if (map.size >= 128) map.remove(map.keys().random())
                map.put(address, tables)
                return tables
            }

            val http = try {
                Json.read(Async.fetchHttpString("http://ip-api.com/json/${encodeURIComponent(address)}?fields=21229056"))
            } catch (e: Throwable) {
                val r = e.stackTraceToString()
                Log.warn("Failed to fetch metadata for IP ${address}.\n$r")
                return null
            }

            if (!http.at("status").isString) {
                Log.err("Failed to fetch metadata for IP ${address}.\nReceived an invalid response from ip-api")
            }
            if (http.at("status").asString() != "success") {
                val message = http.at("message")
                if (message.isNull) Log.warn("Failed to fetch metadata for IP ${address}.")
                else Log.warn("Failed to fetch metadata for IP ${address}.\n${message.asString()}")
                return null
            }

            val tables = IspTables(
                http.at("isp").asString(),
                http.at("as").asString(),
                http.at("asname").asString(),
                http.at("proxy").asBoolean(),
                http.at("hosting").asBoolean(),
                http.at("mobile").asBoolean(),
            )

            Database.abstractQuerySingle(Query(DatabaseScripts.ispsUpdateScript)
                .x("ip", address).x("isp", tables.isp).x("as", tables.ass)
                .x("as_name", tables.asname).x("proxy", tables.proxy)
                .x("hosting", tables.hosting).x("mobile", tables.mobile)).ok()

            if (map.size >= 128) map.remove(map.keys().random())
            map.put(address, tables)
            return tables
        }
    }
}