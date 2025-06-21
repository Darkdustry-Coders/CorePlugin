package mindurka.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mindurka.annotations.PublicAPI
import java.io.InputStream
import kotlinx.serialization.serializer
import com.akuleshov7.ktoml.source.decodeFromStream
import kotlinx.serialization.json.decodeFromStream
import java.io.OutputStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.encodeToString
import java.nio.charset.Charset

@PublicAPI
object Serializers {
    enum class T {
        Toml,
        Json,
    }

    @PublicAPI
    val toml: Toml = Toml(
        TomlInputConfig(
            ignoreUnknownNames = true,
        ),
        TomlOutputConfig(
            indentation = TomlIndentation.FOUR_SPACES,
        ),
    )

    @OptIn(ExperimentalSerializationApi::class)
    @PublicAPI
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        allowComments = true
        allowTrailingComma = true
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified S> deserialize(inp: InputStream, ser: T): S {
        return when (ser) {
            T.Toml -> toml.decodeFromStream(toml.serializersModule.serializer(), inp)
            T.Json -> json.decodeFromStream(inp)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified S> serialize(outp: OutputStream, ser: T, o: S) {
        when (ser) {
            T.Toml -> outp.writer(charset("UTF-8")).append(toml.encodeToString(o))
            T.Json -> json.encodeToStream(o, outp)
        }
    }
}
