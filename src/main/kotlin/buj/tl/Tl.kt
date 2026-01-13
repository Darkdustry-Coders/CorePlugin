package buj.tl

import arc.struct.ObjectMap
import arc.struct.Seq
import mindustry.gen.Groups
import mindustry.gen.Player
import java.io.IOException

private fun minIdx(vararg vals: Int): Int {
    var min = -1
    for (v in vals) if (v != -1 && v < min || min == -1) min = v
    return min
}

private fun destructLocale(locale: String): Array<String> {
    val san = locale.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .replace(Regex("_+"), "_")
        .replace(Regex("^_|_$"), "")
        .lowercase()
    if (san.indexOf('_') > 0) return arrayOf(san, san.take(san.indexOf('_')), "c")
    if (san != "c") return arrayOf(san, "c")
    return arrayOf("c")
}
// @formatter:off
private val COLORS = arrayOf(
    "clear", "black", "white", "lightgray",
    "gray", "darkgray", "blue", "navy",
    "royal", "slate", "sky", "cyan",
    "teal", "green", "acid", "lime",
    "forest", "olive", "yellow", "gold",
    "goldenrod", "orange", "brown", "tan",
    "brick", "red", "scarlet", "coral",
    "salmon", "pink", "magenta", "purple",
    "violet", "maroon",
)
// @formatter:on
private fun isValidColor(color: String): Boolean {
    if (color.startsWith("#")) return color.matches(Regex("^#([0-9]{1,6}|[0-9]{8})$"))
    return color in COLORS
}

private val loaders = Seq<ClassLoader>()
private val localeCache = ObjectMap<ClassLoader, ObjectMap<String, LocaleFile?>>()

private class LocaleFile {
    companion object {
        operator fun get(localeBase: String, key: String): Script {
            var i = loaders.size
            while (--i >= 0) {
                val loader = loaders[i]
                for (locale in destructLocale(localeBase)) {
                    val file = this[loader, locale]
                    if (file == null || !file.tls.containsKey(key)) continue
                    return file.tls.get(key)
                }
            }
            return ScrText("[red]$key[]")
        }

        operator fun get(loader: ClassLoader, locale: String): LocaleFile? {
            val cache = localeCache.get(loader) { ObjectMap() }
            if (cache.containsKey(locale)) {
                return cache.get(locale)
            }

            val str: String
            try {
                val stream = loader.getResourceAsStream("lang/${locale}.l")
                if (stream == null) {
                    cache.put(locale, null)
                    return null
                }
                val bytes = stream.readBytes()
                str = bytes.toString(Charsets.UTF_8)
            } catch (_: IOException) {
                return null
            }

            val file = LocaleFile()
            var prefix = ""
            var name = ""
            var value = ""
            for (line in str.lines()) {
                val trimmed = line.trim()
                if (trimmed.matches(Regex("^\\[[\\w0-9._-]+]$"))) {
                    prefix = trimmed.substring(1, trimmed.length - 1)
                    continue
                }
                val eqIdx = minIdx(line.indexOf('='), line.indexOf(':'))
                if (eqIdx != -1) {
                    val maybeName = line.take(eqIdx).trim()

                    if (maybeName.isEmpty()) {
                        if (line[eqIdx] == '=') {
                            value += "\n" + line.substring(eqIdx + 1).trim()
                        } else if (!line.substring(eqIdx + 1).trim().isEmpty()) {
                            value += " " + line.substring(eqIdx + 1).trim()
                        }
                        continue
                    }

                    if (!name.isEmpty()) file.tls.put(name, Tl.parse(value))
                    name = maybeName
                    if (!prefix.isEmpty()) name = "${prefix}.$name"
                    value = line.substring(eqIdx + 1).trim()
                }
            }
            if (!name.isEmpty()) file.tls.put(name, Tl.parse(value))

            cache.put(locale, file)
            return file
        }
    }

    private val tls = ObjectMap<String, Script>()
}

private fun closeColors(s: String): String {
    val builder = StringBuilder()
    var colorNestness = 0

    var i = 0
    while (true) {
        val prev = i

        i = s.indexOf('[', i)
        if (i == -1) break;

        builder.append(s.substring(prev..i))

        val o = s.indexOf(']', i)
        if (o == -1) break;

        else if (isValidColor(s.substring(i + 1..<o))) colorNestness++

        i = o + 1
    }

    builder.append(builder.drop(i))

    if (builder.isEmpty()) return s
    if (s.endsWith('[')) {
        i = s.length - 1
        while (i >= 0 && s[i] == '[') i--
        i++
        if ((s.length - i) % 2 == 1) "$s[$builder"
    }
    return "$s$builder"
}

class LCtx(private val parent: LCtx? = null) {
    private val tls = ObjectMap<String, Script>()

    /** Add a mapping for a translation key. */
    fun put(key: String, value: Script) {
        tls.put(key, value)
    }

    /** Set translation key to unformatted text. */
    fun put(key: String, text: String) {
        tls.put(key, ScrText(text))
    }

    fun tl(key: String, lang: String): Script {
        if (tls.containsKey(key)) return tls[key]
        return LocaleFile[lang, key]
    }
}

interface L<Self> {
    /** Add a mapping for a translation key. */
    fun put(key: String, value: Script): Self
    /** Set translation key to unformatted text. */
    fun put(key: String, text: String): Self
}

class La(val ctx: LCtx = LCtx()) : L<La> {
    /** Add a mapping for a translation key. */
    override fun put(key: String, value: Script): La {
        ctx.put(key, value)
        return this
    }
    /** Set translation key to unformatted text. */
    override fun put(key: String, text: String): La {
        ctx.put(key, text)
        return this
    }

    fun done(key: String) {
        for (player in Groups.player) {
            Lc(player, ctx).done(key)
        }
    }
}

class Lc(val player: Player, val ctx: LCtx = LCtx()) : L<Lc> {
    /** Add a mapping for a translation key. */
    override fun put(key: String, value: Script): Lc {
        ctx.put(key, value)
        return this
    }
    /** Set translation key to unformatted text. */
    override fun put(key: String, text: String): Lc {
        ctx.put(key, text)
        return this
    }

    fun done(key: String) {
        val l = Ls(player.locale, ctx)
        for (line in l.done(key).lines()) {
            player.sendMessage(line)
        }
    }
}

class Ls(val locale: String, val ctx: LCtx = LCtx()) : L<Ls> {
    /** Add a mapping for a translation key. */
    override fun put(key: String, value: Script): Ls {
        ctx.put(key, value)
        return this
    }
    /** Set translation key to unformatted text. */
    override fun put(key: String, text: String): Ls {
        ctx.put(key, text)
        return this
    }

    fun done(key: String): String {
        val script = Tl.parse(key)
        return script.append(ctx, "", locale)
    }
}

interface Script {
    fun append(ctx: LCtx, source: String, lang: String): String
    fun debug(): String
}

private class ScrCombo(val list: Seq<Script>) : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        var txt = source
        for (scr in list) {
            txt = scr.append(ctx, txt, lang)
        }
        return txt
    }

    override fun debug(): String = "Combo(${list.map { it.debug() }.joinToString(", ")})"
}

private class ScrText(text: String) : Script {
    val text: String

    init {
        // TODO: :emojis:

        var idx = text.lastIndex
        while (idx > 0 && text[idx] != '[' && !text[idx].isWhitespace() && text[idx] != ']') idx--
        this.text =
            if (text[idx] == '[')
                    text.substring(0..<idx.coerceAtMost(1)) + '[' + text.substring(idx)
            else text
    }

    override fun append(ctx: LCtx, source: String, lang: String): String = source + text

    override fun debug(): String = "\"$text\""
}

private class ScrKey(val key: Script) : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        val key = this.key.append(ctx, "", lang)
        return ctx.tl(key, lang).append(ctx, source, lang)
    }

    override fun debug(): String = "Key(${key.debug()})"
}

private class ScrEach(
    val key: String,
    val source: Script,
    val template: Script,
    val separator: Script,
    val join: Script
) : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        var txt = source
        var first = true
        for (name in this.source.append(ctx, "", lang).split(separator.append(ctx, "", lang))) {
            if (first) first = false else txt = join.append(ctx, txt, lang)
            val lctx = LCtx(ctx)
            lctx.put(key, name)
            txt = template.append(lctx, txt, lang)
        }
        return txt
    }

    override fun debug(): String =
        "Each($key in ${source.debug()} split ${separator.debug()} join ${join.debug()})"
}

private class ScrIf(
    val rhs: Script,
    val op: Op,
    val lhs: Script,
    val then: Script,
    val other: Script
) : Script {
    enum class Op {
        Equals,
        EqualsIgnoreCase,
        NotEquals,
        NotEqualsIgnoreCase,
        Contains,
        ContainsIgnoreCase,
        StartsWith,
        EndsWith,
        Greater,
        Smaller,
        GreaterOrEqual,
        SmallerOrEqual,
        Spans,

        ;

        fun debug(): String = when (this) {
            Equals -> "="
            EqualsIgnoreCase -> "=="
            NotEquals -> "!="
            NotEqualsIgnoreCase -> "!=="
            Contains -> "~"
            ContainsIgnoreCase -> "~="
            StartsWith -> "#="
            EndsWith -> "=#"
            Greater -> ">"
            Smaller -> "<"
            GreaterOrEqual -> ">="
            SmallerOrEqual -> "<="
            Spans -> "#~"
        }

        companion object {
            fun fromString(op: String): Op = when (op) {
                "=" -> Equals
                "==" -> EqualsIgnoreCase
                "!=" -> NotEquals
                "!==" -> NotEquals
                "~" -> Contains
                "~=" -> ContainsIgnoreCase
                "#=" -> StartsWith
                "=#" -> EndsWith
                ">" -> Greater
                "<" -> Smaller
                ">=" -> GreaterOrEqual
                "<=" -> SmallerOrEqual
                "#~" -> Spans
                else -> throw RuntimeException("invalid operator '${op}'")
            }
        }
    }

    override fun append(ctx: LCtx, source: String, lang: String): String {
        val rhs = rhs.append(ctx, "", lang)
        val lhs = lhs.append(ctx, "", lang)

        return if (when (op) {
            Op.Equals -> rhs == lhs
            Op.EqualsIgnoreCase -> rhs.equals(lhs, true)
            Op.NotEquals -> rhs != lhs
            Op.NotEqualsIgnoreCase -> !rhs.equals(lhs, true)
            Op.Contains -> lhs in rhs
            Op.ContainsIgnoreCase -> rhs.contains(lhs, true)
            Op.StartsWith -> rhs.startsWith(lhs)
            Op.EndsWith -> rhs.endsWith(lhs)
            Op.Greater -> {
                val rval = rhs.toFloatOrNull()
                val lval = lhs.toFloatOrNull()

                rval != null && lval != null && rval > lval
            }
            Op.Smaller -> {
                val rval = rhs.toFloatOrNull()
                val lval = lhs.toFloatOrNull()

                rval != null && lval != null && rval < lval
            }
            Op.GreaterOrEqual -> {
                val rval = rhs.toFloatOrNull()
                val lval = lhs.toFloatOrNull()

                rval != null && lval != null && rval >= lval
            }
            Op.SmallerOrEqual -> {
                val rval = rhs.toFloatOrNull()
                val lval = lhs.toFloatOrNull()

                rval != null && lval != null && rval <= lval
            }
            Op.Spans -> {
                if (lhs.isEmpty()) true
                else {
                    var i = 0
                    for (ch in rhs) {
                        if (ch == lhs[i]) {
                            i++
                            if (i == lhs.length) break
                        }
                    }
                    i == lhs.length
                }
            }
        }) then.append(ctx, source, lang)
        else other.append(ctx, source, lang)
    }
    override fun debug(): String =
        "If(${rhs.debug()} ${op.debug()} ${lhs.debug()}, ${then.debug()}, ${other.debug()})"
}

private object ScrNone : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String = source
    override fun debug(): String = "<empty>"
}

// General rules for parse* functions:
//
// 1. idx must point to the first character of the expression
// 2. returned idx must be at the character after the expression

private fun parseUnicode(script: String, idx: Array<Int>): Char {
    var num = 0
    if (idx[0] >= script.length || script[idx[0]] != '{')
        throw RuntimeException("invalid unicode escape (Tl unicode escapes must follow the format of \\u{..})")
    while (++idx[0] < script.length && script[idx[0]] != '}') {
        val ch = script[idx[0]]
        num *= 16
        num += when (ch) {
            in '0'..'9' -> ch.code - '0'.code
            in 'a'..'f' -> ch.code - 'a'.code + 10
            in 'A'..'F' -> ch.code - 'A'.code + 10
            else        -> throw RuntimeException("invalid unicode escape (unicode codepoint must be in hex)")
        }
    }
    if (idx[0] >= script.length || script[idx[0]] != '}')
        throw RuntimeException("invalid unicode escape (Tl unicode escapes must follow the format of \\u{..})")
    if (num !in (Char.MIN_VALUE.code..Char.MAX_VALUE.code))
        throw RuntimeException("invalid unicode escape (invalid codepoint $num)")
    idx[0]++
    return num.toChar()
}

private fun parseExpr(script: String, idx: Array<Int>): Script {
    var tempStr = ""
    var depth = Seq<Char>()
    var backspace = false
    idx[0]--
    while (++idx[0] < script.length) {
        if (backspace) {
            backspace = false
            if (script[idx[0]] !in "(){}\\") tempStr += '\\'
            tempStr += script[idx[0]]
            continue
        }
        when (script[idx[0]]) {
            '\\' -> {
                backspace = true
                continue
            }
            '(' -> {
                depth.add(')')
                if (depth.size == 1) continue
            }
            ')' -> {
                if (depth.isEmpty) break
                if (depth.pop() != ')') throw RuntimeException("mismatched bracket. expected ')'")
                if (depth.isEmpty) break
            }
            '{' -> depth.add('}')
            '}' -> {
                if (depth.isEmpty) break
                if (depth.pop() != '}') throw RuntimeException("mismatched bracket. expected '}'")
            }
        }
        if (!depth.isEmpty || !script[idx[0]].isWhitespace() && script[idx[0]] !in "<>=#~!")
                tempStr += script[idx[0]]
        else break
    }
    if (!depth.isEmpty) throw RuntimeException("unenclosed expression")
    return Tl.parse(tempStr)
}

private fun parseEach(script: String, idx: Array<Int>): Script {
    // TODO: {each {name} in {key} <template> split <sep> join <sep> [..if <cond>]}
    // TODO: move to parseExpr

    if (!script.startsWith("{each", idx[0])) throw RuntimeException("not an each expression")
    idx[0] += "{each".length - 1

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '{') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    var key = ""
    while (++idx[0] < script.length &&
        (script[idx[0]].isLetter() ||
            script[idx[0]].isDigit() ||
            script[idx[0]] in "-_.")) key += script[idx[0]]
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '}') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (!script.startsWith("in", idx[0])) throw RuntimeException("couldn't find \"in\" expression")
    idx[0]++

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '{') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    var sourceStr = "{"
    var depth = 1
    var backspace = false
    while (++idx[0] < script.length && depth > 0) {
        if (backspace) {
            backspace = false
            if (script[idx[0]] !in "{}\\") sourceStr += '\\'
            sourceStr += script[idx[0]]
            continue
        }
        when (script[idx[0]]) {
            '\\' -> {
                backspace = true
                continue
            }
            '{' -> depth++
            '}' -> depth--
        }
        sourceStr += script[idx[0]]
    }
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    val source = Tl.parse(sourceStr)

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '(') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    var templateStr = ""
    depth = 1
    backspace = false
    while (++idx[0] < script.length) {
        if (backspace) {
            backspace = false
            if (script[idx[0]] !in "()\\") templateStr += '\\'
            templateStr += script[idx[0]]
            continue
        }
        when (script[idx[0]]) {
            '\\' -> {
                backspace = true
                continue
            }
            '(' -> depth++
            ')' -> depth--
        }
        if (depth > 0) templateStr += script[idx[0]] else break
    }
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    val template = Tl.parse(templateStr)

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (!script.startsWith("split", idx[0]))
        throw RuntimeException("couldn't find \"split\" expression")
    idx[0] += 4

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '(') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    var splitStr = ""
    depth = 1
    backspace = false
    while (++idx[0] < script.length) {
        if (backspace) {
            backspace = false
            if (script[idx[0]] !in "()\\") splitStr += '\\'
            splitStr += script[idx[0]]
            continue
        }
        when (script[idx[0]]) {
            '\\' -> {
                backspace = true
                continue
            }
            '(' -> depth++
            ')' -> depth--
        }
        if (depth > 0) splitStr += script[idx[0]] else break
    }
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    val split = Tl.parse(splitStr)

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (!script.startsWith("join", idx[0]))
            throw RuntimeException("couldn't find \"join\" expression")
    idx[0] += 3

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]] != '(') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    var joinStr = ""
    depth = 1
    backspace = false
    while (++idx[0] < script.length) {
        if (backspace) {
            backspace = false
            if (script[idx[0]] !in "()\\") joinStr += '\\'
            joinStr += script[idx[0]]
            continue
        }
        when (script[idx[0]]) {
            '\\' -> {
                backspace = true
                continue
            }
            '(' -> depth++
            ')' -> depth--
        }
        if (depth > 0) joinStr += script[idx[0]] else break
    }
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    val join = Tl.parse(joinStr)

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    if (idx[0] >= script.length) throw RuntimeException("unenclosed each expression")
    if (script[idx[0]++] != '}') throw RuntimeException("unexpected char '${script[idx[0]]}'")

    return ScrEach(key, source, template, split, join)
}

private fun parseIf(script: String, idx: Array<Int>, afterOpening: Boolean = false): Script {
    if (Regex("^\\{if[ ({]") !in script.substring(idx[0])
        && !(afterOpening && Regex("^if[ ({]") in script.substring(idx[0]))
    ) throw RuntimeException("not an if statement")
    idx[0] += if (afterOpening) "if".length else "{if".length

    do idx[0]++ while (idx[0] < script.length && script[idx[0]].isWhitespace())
    val rhs = parseExpr(script, idx)
    if (idx[0] >= script.length) throw RuntimeException("unenclosed if expression")

    while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
    if (idx[0]-- >= script.length) throw RuntimeException("unenclosed if expression")
    var tempStr = ""
    while (++idx[0] < script.length) {
        if (script[idx[0]] in "<>=#~!") tempStr += script[idx[0]] else break
    }
    val op = ScrIf.Op.fromString(tempStr)

    while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
    val lhs = parseExpr(script, idx)
    if (idx[0] >= script.length) throw RuntimeException("unenclosed if expression")

    while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
    if (Regex("^then[ ({]") !in script.substring(idx[0]))
        throw RuntimeException("missing 'then' expression")
    idx[0] += "then".length
    while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
    val then = parseExpr(script, idx)
    if (idx[0] >= script.length) throw RuntimeException("unenclosed if expression")

    var other: Script = ScrNone
    while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
    if (Regex("^else[ ({]") in script.substring(idx[0])) {
        idx[0] += "else".length
        while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
        other =
            if (Regex("^if[ ({]") in script.substring(idx[0])) parseIf(script, idx, true)
            else parseExpr(script, idx)
    }

    if (!afterOpening) {
        while (idx[0] < script.length && script[idx[0]].isWhitespace()) idx[0]++
        if (idx[0] >= script.length || script[idx[0]++] != '}')
            throw RuntimeException("unenclosed if expression")
    }

    return ScrIf(rhs, op, lhs, then, other)
}

private fun parseKey(script: String, idx: Array<Int>): Script {
    // TODO: {<key> [..with {<key>} = (<value>)]}

    if (script.startsWith("{each{", idx[0]) || script.startsWith("{each ", idx[0]))
        return parseEach(script, idx)
    if (Regex("^\\{if[ ({]") in script.substring(idx[0])) return parseIf(script, idx)

    if (idx[0] >= script.length || script[idx[0]++] != '{')
        throw RuntimeException("invalid key sequence")
    var text = ""
    var depth = 1
    var backslash = false
    do {
        if (backslash) {
            text += script[idx[0]]
            backslash = true
            continue
        }
        when (script[idx[0]]) {
            '{' -> depth++
            '}' -> depth--
            '\\' -> {
                backslash = true
                continue
            }
        }
        if (depth > 0) text += script[idx[0]]
        else {
            idx[0]++
            break
        }
    } while (++idx[0] < script.length)
    return ScrKey(Tl.parse(text))
}

private fun parseRoot(script: String, idx: Array<Int>): Script {
    val combo = Seq<Script>()

    var text = ""
    var backslash = false
    idx[0]--
    while (++idx[0] < script.length) {
        val ch = script[idx[0]]

        if (backslash) {
            backslash = false

            text += when (ch) {
                'u' -> {
                    idx[0]++
                    val o = parseUnicode(script, idx)
                    idx[0]--
                    o
                }
                'n' -> '\n'
                else -> ch
            }

            continue
        }

        when (ch) {
            '\\' -> backslash = true
            '{' -> {
                if (!text.isEmpty()) combo.add(ScrText(text))
                text = ""
                combo.add(parseKey(script, idx))
                idx[0]--
            }
            '}' -> throw RuntimeException("unexpected '}'")
            else -> text += ch
        }
    }
    if (!text.isEmpty()) combo.add(ScrText(text))

    if (combo.isEmpty) return ScrNone
    if (combo.size == 1) return combo[0]
    return ScrCombo(combo)
}

/**
 * Translation API.
 *
 * ```java
 * Tl.send(player).
 * ```
 */
object Tl {
    @JvmStatic fun broadcast(): La = La()
    @JvmStatic fun send(player: Player): Lc = Lc(player)
    @JvmStatic fun fmtFor(player: Player): Ls = Ls(player.locale)
    @JvmStatic fun fmt(locale: String): Ls = Ls(locale)
    @JvmStatic fun fmt(player: Player): Ls = Ls(player.locale)

    @JvmStatic
    fun init(loader: ClassLoader) {
        loaders.add(loader)
    }

    @JvmStatic
    fun parse(script: String): Script {
        val idx = arrayOf(0)
        val script = parseRoot(script, idx)
        return script
    }
}

object Tlu {
    fun <R, T : L<R>> list(l: T, list: Iterable<String>, key: String, sep: String = ","): T {
        l.put(key, list.joinToString(sep))
        l.put("${key}.len", list.count().toString())
        return l
    }
}
