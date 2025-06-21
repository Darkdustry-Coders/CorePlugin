package mindurka.config

import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import mindustry.Vars
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import arc.files.Fi

@Serializable
data class GlobalConfig(
    val serverIp: String = "127.0.0.1",
) {
    companion object {
        val instance: GlobalConfig = run {
            val file = Fi.get(CorePluginConfig.instance.globalConfigPath)
            var instance: GlobalConfig
            try {
                instance = Serializers.toml.decodeFromStream(file.read(8192))
            } catch (_: IOException) {
                instance = GlobalConfig(
                    serverIp = run {
                        val con = URI.create("https://ip.me/").toURL().openConnection() as HttpURLConnection
                        con.requestMethod = "GET"
                        con.doInput = true
                        con.inputStream.readAllBytes().toString(Charsets.UTF_8)
                    }
                )
                if (!file.exists()) try {
                    file.writeString(Serializers.toml.encodeToString(instance))
                } catch (_: Exception) {}
            } catch (e: Exception) {
                throw RuntimeException("Failed to load 'glocalConfig.toml'! Please check whether the file is in correct format!")
            }
            instance
        }
    }
}
