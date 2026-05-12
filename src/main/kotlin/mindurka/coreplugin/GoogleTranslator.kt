package mindurka.coreplugin

import arc.util.Log
import arc.util.Strings
import arc.util.serialization.JsonReader
import mindurka.util.Async
import mindurka.util.splitOnceFirst
import mindustry.Vars
import java.io.ByteArrayInputStream

// https://docs.cloud.google.com/translate/docs/languages
class LanguageCode(val name: String, val code: String)
val supportedLanguageCodes = arrayOf(
    LanguageCode("Afrikaans", "af"),
    LanguageCode("Albanian", "sq"),
    LanguageCode("Amharic", "am"),
    LanguageCode("Arabic (Saudi Arabia)", "ar-SA"),
    LanguageCode("Arabic", "ar"),
    LanguageCode("Armenian", "hy"),
    LanguageCode("Azerbaijani", "az"),
    LanguageCode("Basque", "eu"),
    LanguageCode("Belarusian", "be"),
    LanguageCode("Bengali (India)", "bn-IN"),
    LanguageCode("Bengali", "bn"),
    LanguageCode("Bosnian (Cyrillic)", "bs-Cyrl"),
    LanguageCode("Bosnian", "bs"),
    LanguageCode("Bulgarian", "bg"),
    LanguageCode("Burmese", "my"),
    LanguageCode("Catalan", "ca"),
    LanguageCode("Chinese (China)", "zh-CH"),
    LanguageCode("Chinese (Hong Kong)", "zh-HK"),
    LanguageCode("Chinese (Simplified)", "zh-Hans"),
    LanguageCode("Chinese (Taiwan)", "zh-TW"),
    LanguageCode("Chinese (Traditional)", "zh-Hant"),
    LanguageCode("Chinese", "zh"),
    LanguageCode("Croatian", "hr"),
    LanguageCode("Czech", "cs"),
    LanguageCode("Danish", "da"),
    LanguageCode("Dutch (Belgium)", "nl-BE"),
    LanguageCode("Dutch", "nl"),
    LanguageCode("English (Australia)", "en-AU"),
    LanguageCode("English (Canada)", "en-CA"),
    LanguageCode("English (New Zealand)", "en-NZ"),
    LanguageCode("English (Philippines)", "en-PH"),
    LanguageCode("English (South Africa)", "en-ZA"),
    LanguageCode("English (United Kingdom)", "en-GB"),
    LanguageCode("English (United States)", "en-US"),
    LanguageCode("English", "en"),
    LanguageCode("Estonian", "et"),
    LanguageCode("Filipino", "fil"),
    LanguageCode("Finnish", "fi"),
    LanguageCode("French (Canada)", "fr-CA"),
    LanguageCode("French (Switzerland)", "fr-CH"),
    LanguageCode("French", "fr"),
    LanguageCode("Frisian", "fy"),
    LanguageCode("Galician", "gl"),
    LanguageCode("Georgian", "ka"),
    LanguageCode("German", "de"),
    LanguageCode("Greek", "el"),
    LanguageCode("Guarani", "gn"),
    LanguageCode("Gujarati", "gu"),
    LanguageCode("Hausa", "ha"),
    LanguageCode("Hebrew", "he"),
    LanguageCode("Hebrew", "iw"),
    LanguageCode("Hindi", "hi"),
    LanguageCode("Hindi", "hu"),
    LanguageCode("Icelandic", "is"),
    LanguageCode("Igbo", "ig"),
    LanguageCode("Indonesian", "id"),
    LanguageCode("Irish", "ga"),
    LanguageCode("Italian", "it"),
    LanguageCode("Japanese", "ja"),
    LanguageCode("Kannada", "kn"),
    LanguageCode("Khmer", "km"),
    LanguageCode("Korean", "ko"),
    LanguageCode("Kyrgyz", "ky"),
    LanguageCode("Lao", "lo"),
    LanguageCode("Latvian", "lv"),
    LanguageCode("Lingala", "ln"),
    LanguageCode("Lithuanian", "lt"),
    LanguageCode("Luxembourgish", "lb"),
    LanguageCode("Macedonian", "mk"),
    LanguageCode("Malay", "ms"),
    LanguageCode("Malayalam", "ml"),
    LanguageCode("Maltese", "mt"),
    LanguageCode("Marathi", "mr"),
    LanguageCode("Mongolian", "mn"),
    LanguageCode("Nepali", "ne"),
    LanguageCode("Norwegian Bokmal", "nb"),
    LanguageCode("Norwegian", "no"),
    LanguageCode("Odia", "or"),
    LanguageCode("Persian", "fa"),
    LanguageCode("Polish", "pl"),
    LanguageCode("Portuguese (Brazil)", "pt-BR"),
    LanguageCode("Portuguese (Portugal)", "pt-PT"),
    LanguageCode("Portuguese", "pt"),
    LanguageCode("Punjabi (Pakistan)", "pa-PK"),
    LanguageCode("Punjabi", "pa"),
    LanguageCode("Romanian", "ro"),
    LanguageCode("Russian", "ru"),
    LanguageCode("Scots Gaelic", "gd"),
    LanguageCode("Serbian", "sr"),
    LanguageCode("Slovak", "sk"),
    LanguageCode("Slovenian", "sl"),
    LanguageCode("Somali", "so"),
    LanguageCode("Spanish (Argentina)", "es-AR"),
    LanguageCode("Spanish (Chile)", "es-CL"),
    LanguageCode("Spanish (Colombia)", "es-CO"),
    LanguageCode("Spanish (Costa Rica)", "es-CR"),
    LanguageCode("Spanish (Ecuador)", "es-EC"),
    LanguageCode("Spanish (El Salvador)", "es-SV"),
    LanguageCode("Spanish (Guatemala)", "es-GT"),
    LanguageCode("Spanish (Haiti)", "es-HT"),
    LanguageCode("Spanish (Honduras)", "es-HN"),
    LanguageCode("Spanish (Latin America)", "es-419"),
    LanguageCode("Spanish (Mexico)", "es-MX"),
    LanguageCode("Spanish (Nicaragua)", "es-NI"),
    LanguageCode("Spanish (Panama)", "es-PA"),
    LanguageCode("Spanish (Paraguay)", "es-PV"),
    LanguageCode("Spanish (Peru)", "es-PE"),
    LanguageCode("Spanish (Puerto Rico)", "es-PR"),
    LanguageCode("Spanish (Spain)", "es-ES"),
    LanguageCode("Spanish (United States)", "es-US"),
    LanguageCode("Spanish (Uruguay)", "es-UY"),
    LanguageCode("Spanish (Venezuela)", "es-VE"),
    LanguageCode("Spanish", "es"),
    LanguageCode("Swahili", "sw"),
    LanguageCode("Swedish", "sv"),
    LanguageCode("Tagalog", "tl"),
    LanguageCode("Tajik", "tg"),
    LanguageCode("Tamil", "ta"),
    LanguageCode("Telugu", "te"),
    LanguageCode("Thai", "th"),
    LanguageCode("Turkish", "tr"),
    LanguageCode("Ukrainian", "uk"),
    LanguageCode("Urdu", "ur"),
    LanguageCode("Uzbek", "uz"),
    LanguageCode("Vietnamese", "vi"),
    LanguageCode("Welsh", "cy"),
    LanguageCode("Zulu", "zu"),
)
val auto = LanguageCode("Detect Language", "auto")
fun languageCodeFor(locale: String): LanguageCode? =
    supportedLanguageCodes.find { it.code.equals(locale, true) } ?:
    supportedLanguageCodes.find { splitOnceFirst(it.code, "-").equals(splitOnceFirst(locale, "-"), true) }

private val translationApiUrl = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t"

class Translation(val text: String, val language: String)
suspend fun translateFor(source: String, from: LanguageCode, to: LanguageCode): Translation? {
    try {
        if (to === from) return null
        val out = Async.postHttpString(translationApiUrl, ByteArrayInputStream("tl=${to.code}&sl=${from.code}&q=${Strings.encode(Strings.stripColors(source))}".toByteArray(Vars.charset)))
        val arr = JsonReader().parse(out).child()
        return Translation(arr[0].asString(), arr[1].asString())
    } catch (e: Exception) {
        Log.err("Translation failed", e)
        return null
    }
}
