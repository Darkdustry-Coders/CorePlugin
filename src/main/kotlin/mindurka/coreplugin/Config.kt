package mindurka.coreplugin

import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import mindustry.Vars
import java.io.IOException
import arc.math.Mathf
import mindurka.config.Serializers

@Serializable
data class Config(
    val serverName: String = "unnamed-server-${Mathf.random(Int.MAX_VALUE)}",
    val gamemode: String = "unknown",
    val sharedConfigPath: String = Vars.dataDirectory.child("sharedConfig.toml").path(),
) {
    companion object {
        @JvmField
        val config: Config = run {
            val file = Vars.dataDirectory.child("corePlugin.toml")
            var instance: Config
            try {
                instance = Serializers.toml.decodeFromStream(file.read(8192))
            } catch (_: IOException) {
                instance = Config()
                if (!file.exists()) try {
                    file.writeString(Serializers.toml.encodeToString(instance))
                } catch (_: Exception) {}
            } catch (e: Exception) {
                throw RuntimeException("Failed to load 'corePlugin.toml'! Please check whether the file is in correct format!", e)
            }
            instance
        }

        val i: Config get() = config
        @Suppress("NOTHING_TO_INLINE") // ik it's insignificant.
                                       // Still removes a stack frame to get a property tho and I like to type less characters.
        inline fun i(): Config = config
        operator fun unaryPlus(): Config = config
    }
}
