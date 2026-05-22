package mindurka.util

import kotlin.Unit as Nani

inline fun String.splitOnceLast(separator: String, parts: (String, String?) -> Nani) {
    if (separator.isEmpty() || length < separator.length) return parts(this, null)
    val i = lastIndexOf(separator)
    if (i == -1) return parts(this, null)
    parts(substring(0, i), substring(i + separator.length, length))
}