package mindurka.config

import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import mindustry.Vars
import java.io.IOException

@Serializable
data class CorePluginConfig(
    val globalConfigPath: String = Vars.dataDirectory.child("globalConfig.toml").path(),
) {
    companion object {
        val instance: CorePluginConfig = run {
            val file = Vars.dataDirectory.child("corePlugin.toml")
            var instance: CorePluginConfig
            try {
                instance = Serializers.toml.decodeFromStream(file.read(8192))
            } catch (_: IOException) {
                instance = CorePluginConfig()
                if (!file.exists()) try {
                    file.writeString(Serializers.toml.encodeToString(instance))
                } catch (_: Exception) {}
            } catch (e: Exception) {
                throw RuntimeException("Failed to load 'corePlugin.toml'! Please check whether the file is in correct format!")
            }
            instance
        }
    }
}
