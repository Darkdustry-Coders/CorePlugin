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
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.decodeFromStream
import java.io.OutputStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.encodeToString
import java.nio.charset.Charset
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
@PublicAPI
object Serializers {
    @PublicAPI
    @JvmStatic
    val toml: Toml = Toml(
        TomlInputConfig(
            ignoreUnknownNames = true,
        ),
        TomlOutputConfig(
            indentation = TomlIndentation.FOUR_SPACES,
        ),
    )

    @PublicAPI
    @JvmStatic
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

    @PublicAPI
    @JvmStatic
    val cbor: Cbor = Cbor {
        ignoreUnknownKeys = true
    }
}
