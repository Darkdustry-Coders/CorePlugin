package mindurka.config

import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import mindustry.Vars
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import arc.files.Fi
import mindurka.coreplugin.Config as CorePluginConfig

@Serializable
data class GlobalConfig(
    val serverIp: String = "127.0.0.1",
    val rabbitMqUrl: String = "",
) {
    companion object {
        @JvmStatic
        val globalConfig: GlobalConfig = run {
            val file = Fi.get((+CorePluginConfig).globalConfigPath)
            var instance: GlobalConfig
            try {
                instance = Serializers.toml.decodeFromStream(file.read(8192))
            } catch (_: IOException) {
                instance = GlobalConfig(
                    serverIp = run {
                        val con = URI.create("https://ip.me/").toURL().openConnection() as HttpURLConnection
                        con.requestMethod = "GET"
                        con.doInput = true
                        con.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
                    }
                )
                if (!file.exists()) try {
                    file.writeString(Serializers.toml.encodeToString(instance))
                } catch (_: Exception) {}
            } catch (e: Exception) {
                throw RuntimeException("Failed to load 'globalConfig.toml'! Please check whether the file is in the correct format!")
            }
            instance
        }

        val i: GlobalConfig get() = globalConfig

        @Suppress("NOTHING_TO_INLINE") // ik it's insignificant.
                                       // Still removes a stack frame to get a property tho and I like to type less characters.
        inline fun i(): GlobalConfig = globalConfig
        operator fun unaryPlus(): GlobalConfig = globalConfig
    }
}
