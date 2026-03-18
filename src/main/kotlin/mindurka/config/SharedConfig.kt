package mindurka.config

import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import arc.files.Fi
import arc.util.Log
import mindurka.coreplugin.Config as CorePluginConfig

@Serializable
data class SharedConfig(
    val serverIp: String = "127.0.0.1",
    val rabbitMqUrl: String = "",
    val surrealDbUrl: String = "",
) {
    companion object {
        @JvmStatic
        val sharedConfig: SharedConfig = run {
            val file = Fi.get((+CorePluginConfig).sharedConfigPath)
            var instance: SharedConfig
            try {
                instance = Serializers.toml.decodeFromStream(file.read(8192))
            } catch (_: IOException) {
                instance = SharedConfig(
                    serverIp = run {
                        val con = URI.create("https://ip.me/").toURL().openConnection() as HttpURLConnection
                        con.requestMethod = "GET"
                        con.doInput = true
                        val txt = con.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
                        Log.warn("Fetching IP via an external service! Current IP: $txt")
                        txt
                    }
                )
                if (!file.exists()) try {
                    file.writeString(Serializers.toml.encodeToString(instance))
                } catch (_: Exception) {}
            } catch (e: Exception) {
                throw RuntimeException("Failed to load 'sharedConfig.toml'! Please check whether the file is in the correct format!", e)
            }
            instance
        }

        val i: SharedConfig get() = sharedConfig

        @Suppress("NOTHING_TO_INLINE") // ik it's insignificant.
                                                 // Still removes a stack frame to get a property tho and I like to type less characters.
        inline fun i(): SharedConfig = sharedConfig
        operator fun unaryPlus(): SharedConfig = sharedConfig
    }
}
