package buj.tl

import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Log
import mindurka.util.newSeq
import mindurka.util.seqOf
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
    if (color.startsWith("#")) return color.matches(Regex("^#([0-9a-fA-F]{1,6}|[0-9a-fA-F]{8})$"))
    return color in COLORS
}

private val loaders = Seq<ClassLoader>()
private val localeCache = ObjectMap<ClassLoader, ObjectMap<String, LocaleFile?>>()

internal class LocaleFile {
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

            val file = parse(str)

            cache.put(locale, file)
            return file
        }

        internal fun parse(str: String): LocaleFile {
            val span = StringSpan(str)

            val file = LocaleFile()
            var prefix = ""
            var name = ""
            var collection = TemplateSpanCollection()

            while (!span.isEmpty) {
                span.trimStart()

                if (span.stripPrefix("#")) {
                    if (name.isNotEmpty()) {
                        if (file.tls.put(name, Tl.parse(collection.intoSpanCollection())) != null) throw RuntimeException("duplicate key $name")
                        collection = TemplateSpanCollection()
                    }

                    name = ""
                    while (!span.isEmpty && span[0] != '\n') span.inc()
                    continue
                }

                if (span.stripPrefix("[")) {
                    if (name.isNotEmpty()) {
                        if (file.tls.put(name, Tl.parse(collection.intoSpanCollection())) != null) throw RuntimeException("duplicate key $name")
                        collection = TemplateSpanCollection()
                    }

                    name = ""
                    span.trimStart()
                    prefix = buildString {
                        while (!span.isEmpty && (span[0].isLetterOrDigit() || span[0] in "-_.")) append(span.next())
                    }
                    span.trimStart()
                    if (!span.stripPrefix("]")) throw RuntimeException("unenclosed prefix statement at ${span.at}")
                    continue
                }

                if (span.stripPrefix("=")) {
                    if (name.isEmpty()) throw RuntimeException("attempt to append a value despite not having an entry open")

                    // It's fine since it's just one character
                    collection.growFrom(StringSpan("\n"))
                    span.stripPrefix(" ")
                    while (!span.isEmpty && span[0] != '\n') {
                        if (span[0] == '\r') {
                            span.inc()
                            continue
                        }
                        collection.growFrom(span)
                        span.inc()
                    }
                    continue
                }

                // Both do not do the same thing anymore.
                if (span.stripPrefix("-")) {
                    if (name.isEmpty()) throw RuntimeException("attempt to append a value despite not having an entry open")

                    collection.growFrom(StringSpan(" "))
                    span.stripPrefix(" ")
                    while (!span.isEmpty && span[0] != '\n') {
                        if (span[0] == '\r') {
                            span.inc()
                            continue
                        }
                        collection.growFrom(span)
                        span.inc()
                    }
                    continue
                }

                if (span.stripPrefix(":")) {
                    if (name.isEmpty()) throw RuntimeException("attempt to append a value despite not having an entry open")

                    span.stripPrefix(" ")
                    while (!span.isEmpty && span[0] != '\n') {
                        if (span[0] == '\r') {
                            span.inc()
                            continue
                        }
                        collection.growFrom(span)
                        span.inc()
                    }
                    continue
                }

                if (name.isNotEmpty()) {
                    if (file.tls.put(name, Tl.parse(collection.intoSpanCollection())) != null) throw RuntimeException("duplicate key $name")
                    collection = TemplateSpanCollection()
                }

                val at = span.at

                name = buildString {
                    while (!span.isEmpty && (span[0].isLetterOrDigit() || span[0] in "-_.")) append(span.next())
                }

                if (name.isEmpty()) continue

                name = (if (prefix.isEmpty()) { "" } else { "$prefix." }) + name
                if (name.startsWith(".")) throw RuntimeException("entry name cannot start with a dot ($name) at $at")
                if (name.endsWith(".")) throw RuntimeException("entry name cannot end with a dot ($name) at $at")
                if (name.contains("..")) throw RuntimeException("entry name cannot contain 2 consecutive dots ($name) at $at")

                span.trimStart()
                if (!span.stripPrefix("=")) throw RuntimeException("'=' must be used to assign a value to the entry at ${span.at}")

                val t = StringBuilder()

                span.stripPrefix(" ")
                while (!span.isEmpty && span[0] != '\n') {
                    if (span[0] == '\r') {
                        span.inc()
                        continue
                    }
                    collection.growFrom(span)
                    span.inc()
                }
            }

            if (name.isNotEmpty()) {
                if (file.tls.put(name, Tl.parse(collection.intoSpanCollection())) != null) throw RuntimeException("duplicate key $name")
            }

            return file
        }
    }

    private val tls = ObjectMap<String, Script>()

    operator fun get(key: String): Script? = if (tls.containsKey(key)) tls[key] else null
}

private fun encloseColors(s: String): String {
    var s = s

    if (s.isEmpty()) return s

    var colorNestness = 0
    var ptr = 0

    while (run { ptr = s.indexOf('[', ptr); ptr } != -1) {
        val start = ptr
        ptr = s.indexOf(']', ptr)
        if (ptr == -1) break

        val maybeColor = s.substring(start + 1, ptr)
        if (maybeColor.contains('[')) {
            ptr = start + 1 + maybeColor.indexOf('[')
            continue
        }
        if (isValidColor(maybeColor)) colorNestness += 1
        else if (start + 1 == ptr) {
            if (colorNestness == 0) {
                s = if (ptr + 1 == s.length) s.substring(0, start)
                    else s.substring(0, start) + s.substring(ptr + 1)
                ptr -= 2
            } else {
                colorNestness -= 1
            }
        }
    }

    return closeColors(s) + run { val b = StringBuilder(colorNestness * 2); repeat(colorNestness) { b.append("[]") }; b }
}

private fun closeColors(s: String): String {
    if (s.isEmpty()) return s

    var ptr = s.length - 1
    while ((s[ptr] in 'a'..'f' || s[ptr] in 'A'..'F' || s[ptr] == '#') && ptr > 0) ptr--
    if (s[ptr] != '[') return s
    val start = ptr
    while (s[ptr] == '[' && ptr > 0) ptr--
    if ((start - ptr) % 2 == 1) return s

    return s.substring(0, start) + "["
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

interface L<Self: L<Self>> {
    /** Add a mapping for a translation key. */
    fun put(key: String, value: Script): Self
    /** Set translation key to an unformatted text. */
    fun put(key: String, text: String): Self
}

interface LVoidDone<Self: LVoidDone<Self>> : L<Self> {
    fun done(key: String)
}

class La(val ctx: LCtx = LCtx()) : LVoidDone<La> {
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

    override fun done(key: String) {
        for (player in Groups.player) {
            Lc(player, ctx).done(key)
        }
    }
}

class Lc(val player: Player, val ctx: LCtx = LCtx()) : LVoidDone<Lc> {
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

    override fun done(key: String) {
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

private class ScrText(val text: String) : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String = source + text
    override fun debug(): String = "\"$text\""
}

private class ScrKey(val key: Script) : Script {
    override fun append(ctx: LCtx, source: String, lang: String): String {
        val key = this.key.append(ctx, "", lang)
        val inner = encloseColors(ctx.tl(key, lang).append(ctx, "", lang))
        return closeColors(source) + inner
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
            StartsWith -> "&="
            EndsWith -> "=&"
            Greater -> ">"
            Smaller -> "<"
            GreaterOrEqual -> ">="
            SmallerOrEqual -> "<="
            Spans -> "&~"
        }

        companion object {
            fun fromString(op: String): Op = when (op) {
                "=" -> Equals
                "==" -> EqualsIgnoreCase
                "!=" -> NotEquals
                "!==" -> NotEqualsIgnoreCase
                "~" -> Contains
                "~=" -> ContainsIgnoreCase
                "&=" -> StartsWith
                "=&" -> EndsWith
                ">" -> Greater
                "<" -> Smaller
                ">=" -> GreaterOrEqual
                "<=" -> SmallerOrEqual
                "&~" -> Spans
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

/**
 * ```
 * &=
 * ^^*
 * ```
 */
private fun parseOp(script: SpanCollection): ScrIf.Op? {
    script.trimStart()
    if (script.stripPrefix("==")) return ScrIf.Op.EqualsIgnoreCase
    if (script.stripPrefix("=")) return ScrIf.Op.Equals
    if (script.stripPrefix("!==")) return ScrIf.Op.NotEqualsIgnoreCase
    if (script.stripPrefix("!=")) return ScrIf.Op.NotEquals
    if (script.stripPrefix("~=")) return ScrIf.Op.ContainsIgnoreCase
    if (script.stripPrefix("~")) return ScrIf.Op.Contains
    if (script.stripPrefix("&=")) return ScrIf.Op.StartsWith
    if (script.stripPrefix("=&")) return ScrIf.Op.EndsWith
    if (script.stripPrefix(">=")) return ScrIf.Op.GreaterOrEqual
    if (script.stripPrefix(">")) return ScrIf.Op.Greater
    if (script.stripPrefix("<=")) return ScrIf.Op.SmallerOrEqual
    if (script.stripPrefix("<")) return ScrIf.Op.Smaller
    if (script.stripPrefix("&~")) return ScrIf.Op.Spans
    return null
}

/**
 * ```
 * \u{ACB}
 *   ^^^^^*
 * ```
 */
private fun parseUnicode(script: SpanCollection): Char {
    var num = 0
    if (script.isEmpty || script[0] != '{')
        throw RuntimeException("invalid unicode escape (Tl unicode escapes must follow the format of \\u{..}) at ${script.at}")
    while (!script.incIsEmpty() && script[0] != '}') {
        val ch = script[0]
        num *= 16
        num += when (ch) {
            in '0'..'9' -> ch.code - '0'.code
            in 'a'..'f' -> ch.code - 'a'.code + 10
            in 'A'..'F' -> ch.code - 'A'.code + 10
            else        -> throw RuntimeException("invalid unicode escape (unicode codepoint must be in hex) at ${script.at}")
        }
    }
    if (script.isEmpty || script[0] != '}')
        throw RuntimeException("invalid unicode escape (Tl unicode escapes must follow the format of \\u{..}) at ${script.at}")
    if (num !in (Char.MIN_VALUE.code..Char.MAX_VALUE.code))
        throw RuntimeException("invalid unicode escape (invalid codepoint $num) at ${script.at}")
    script.inc()
    return num.toChar()
}

/**
 * ```
 * Hello, world
 * ^^^^^^*
 *
 * (Hiii (e) )
 * ^^^^^^^^^^^*
 *
 * {some.key}
 * ^^^^^^^^^^*
 * ```
 */
private fun parseExpr(script: SpanCollection): Script {
    script.trimStart()
    if (script.isEmpty) throw RuntimeException("input ended before an expression could have been parsed at ${script.at}")

    var keepOn0 = true

    when (script[0]) {
        '}' -> throw RuntimeException("unexpected char '}' when parsing an expression at ${script.at}")
        ')' -> throw RuntimeException("unexpected char ')' when parsing an expression at ${script.at}")
        '\\' -> script.inc()
        '(' -> {
            keepOn0 = false
            script.inc()
        }
        '{' -> return parseKey(script)
    }

    val list = newSeq<Script>()

    var depth = if (keepOn0) 0 else 1
    var backspace = false
    val text = StringBuilder()

    while (depth > 0 || keepOn0) {
        if (script.isEmpty) throw RuntimeException("input ended before an expression could have been parsed at ${script.at}")
        val chr = script[0]
        if (backspace) {
            when (chr) {
                'u' -> text.append(parseUnicode(script))
                'n' -> text.append('\n')
                else -> text.append(chr)
            }
            script.inc()
            backspace = false
            continue
        }
        if (chr == '}') {
            if (depth == 0) break
            throw RuntimeException("unexpected char '}' when parsing an expression at ${script.at}")
        }
        if (chr == ')') {
            if (depth > 1 || keepOn0) text.append(')')
            if (depth == 0) throw RuntimeException("closing a string without an opened string at ${script.at}")
            depth--
            script.inc()
            continue
        }
        if (chr == '\\') {
            backspace = true
            script.inc()
            continue
        }
        if (chr == '(') {
            text.append('(')
            depth++
            script.inc()
            continue
        }
        if (chr == '{') {
            if (!text.isEmpty()) {
                list.add(ScrText(text.toString()))
                text.setLength(0)
            }

            list.add(parseKey(script))

            continue
        }
        if (depth == 0 && chr.isWhitespace()) break

        text.append(chr)
        script.inc()
        continue
    }

    if (!text.isEmpty()) list.add(ScrText(text.toString()))

    if (list.size == 0) return ScrNone
    if (list.size == 1) return list[0]
    return ScrCombo(list)
}

/**
 * ```
 * {each {name} in {key} split {sep} join {sep}}
 *      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^*
 * ```
 */
private fun parseEach(script: SpanCollection): Script {
    // TODO: {each {name} in {key} <template> split <sep> join <sep> [..if <cond>]}

    script.trimStart()
    if (script.isEmpty) throw RuntimeException("missing variable at ${script.at}")
    if (!script.stripPrefix("{")) throw RuntimeException("expected char '{', found '${script[0]}' at ${script.at}")
    script.trimStart()

    val name = buildString { while (true) {
        if (script.isEmpty) throw RuntimeException("missing variable at ${script.at}")
        val chr = script[0]
        if (!chr.isLetterOrDigit() && chr !in "-_.") break
        script.inc()
        append(chr)
    } }
    script.trimStart()

    if (!script.stripPrefix("}")) throw RuntimeException("expected char '}', found '${script[0]}' at ${script.at}")
    script.trimStart()

    if (script.isEmpty) throw RuntimeException("missing \"in\" at ${script.at}")
    if (!script.stripPrefix("in")) throw RuntimeException("expected \"in\", found char '${script[0]}' at ${script.at}")

    val key = parseExpr(script)
    val template = parseExpr(script)
    script.trimStart()

    if (script.isEmpty) throw RuntimeException("missing \"split\" at ${script.at}")
    if (!script.stripPrefix("split")) throw RuntimeException("expected \"split\", found char '${script[0]}' at ${script.at}")

    val split = parseExpr(script)
    script.trimStart()

    if (script.isEmpty) throw RuntimeException("missing \"join\" at ${script.at}")
    if (!script.stripPrefix("join")) throw RuntimeException("expected \"join\", found char '${script[0]}' at ${script.at}")

    val join = parseExpr(script)
    script.trimStart()

    if (!script.stripPrefix("}")) throw RuntimeException("could not close a 'for' statement at ${script.at}")

    return ScrEach(name, key, template, split, join)
}

/**
 * ```
 * {if a = b then hi else if a = a then hoi else eh}
 *     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^*
 * ```
 */
private fun parseIf(script: SpanCollection): Script {
    val lhs = parseExpr(script)
    val op = parseOp(script) ?: throw RuntimeException("expected an operator, found char '${script[0]}' at ${script.at}")
    val rhs = parseExpr(script)

    script.trimStart()
    if (script.isEmpty) throw RuntimeException("missing \"then\" at ${script.at}")
    if (!script.stripPrefix("then")) throw RuntimeException("expected \"then\", found char '${script[0]}' at ${script.at}")

    val then = parseExpr(script)

    script.trimStart()
    if (script.stripPrefix("}")) return ScrIf(rhs, op, lhs, then, ScrNone)

    if (!script.stripPrefix("else")) throw RuntimeException("missing \"else\" at ${script.at}")
    script.trimStart()
    if (script.stripPrefix("if")) return ScrIf(rhs, op, lhs, then, parseIf(script))

    val other = parseExpr(script)
    script.trimStart()
    if (script.isEmpty) throw RuntimeException("missing '}' at ${script.at}")
    if (!script.stripPrefix("}")) throw RuntimeException("expected char '}', found '${script[0]}' at ${script.at}")

    return ScrIf(rhs, op, lhs, then, other)
}

/**
 * ```
 * {hello}
 * ^^^^^^^*
 * ```
 */
private fun parseKey(script: SpanCollection): Script {
    // TODO: {<key> [..with {<key>} = (<value>)]}

    assert(script.stripPrefix("{"))
    script.trimStart()

    if (script.startsWith("each ") || script.startsWith("each{")) {
        script.stripPrefix("each")
        return parseEach(script)
    }
    if (script.startsWith("if ") || script.startsWith("if{")) {
        script.stripPrefix("if")
        return parseIf(script)
    }

    return ScrKey(parseRoot(script, true))
}

private fun parseRoot(script: SpanCollection, insideBlock: Boolean = false): Script {
    val combo = Seq<Script>()

    val text = StringBuilder()
    var backslash = false
    while (!script.isEmpty) {
        val ch = script[0]

        if (backslash) {
            backslash = false
            script.inc()

            text.append(when (ch) {
                'u' -> parseUnicode(script)
                'n' -> '\n'
                else -> ch
            })

            continue
        }

        when (ch) {
            '\\' -> {
                backslash = true
                script.inc()
            }
            '{' -> {
                if (!text.isEmpty()) combo.add(ScrText(text.toString()))
                text.setLength(0)
                combo.add(parseKey(script))
            }
            '}' -> {
                if (insideBlock) {
                    script.inc()

                    if (!text.isEmpty()) combo.add(ScrText(text.toString()))

                    if (combo.isEmpty) return ScrNone
                    if (combo.size == 1) return combo[0]
                    return ScrCombo(combo)
                }
                throw RuntimeException("unexpected '}' at ${script.at}")
            }
            else -> {
                text.append(ch)
                script.inc()
            }
        }
    }
    if (insideBlock) throw RuntimeException("unenclosed key at ${script.at}")
    if (!text.isEmpty()) combo.add(ScrText(text.toString()))

    if (combo.isEmpty) return ScrNone
    if (combo.size == 1) return combo[0]
    return ScrCombo(combo)
}

internal class SpanCollection(val spans: Seq<StringSpan>) {
    constructor(span: StringSpan) : this(seqOf(span))

    init { assert(!spans.isEmpty) }

    val nonEmptySpan: StringSpan? get() {
        for (span in spans) {
            if (span.isEmpty) continue
            return span
        }
        return null
    }
    val length get() = spans.sum { it.length }
    val line get() = span.line
    val column get() = span.column
    val start get() = span.start
    val source get() = span.source
    val span: StringSpan get() = nonEmptySpan ?: spans.last()
    val at get() = "$line:$column"
    val isEmpty get() = spans.all { it.isEmpty }

    operator fun get(pos: Int): Char {
        if (pos < 0) throw IndexOutOfBoundsException("Index is negative")
        var p = pos
        for (span in spans) {
            if (span.length <= p) {
                p -= span.length
                continue
            }
            return span[p]
        }
        throw IndexOutOfBoundsException("Index is too large ($pos >= $length)")
    }

    fun inc(): Int {
        for (span in spans) {
            if (span.isEmpty) continue
            span.inc()
            break
        }

        return length
    }

    fun incIsEmpty(): Boolean {
        for (span in spans) {
            if (span.isEmpty) continue
            span.inc()
            break
        }

        return isEmpty
    }

    fun incAfter(): Int {
        val length = length

        for (span in spans) {
            if (span.isEmpty) continue
            span.inc()
            break
        }

        return length
    }

    fun dec(): Int {
        for (i in 0..<spans.size) {
            val span = spans[spans.size - i - 1]
            if (span.length == span.initalLength) continue
            span.dec()
            return length
        }
        throw IndexOutOfBoundsException("Cannot subtract any more")
    }

    fun stripPrefix(prefix: String): Boolean {
        for (span in spans) {
            if (span.isEmpty) continue
            return span.stripPrefix(prefix)
        }
        return false
    }

    fun startsWith(prefix: String): Boolean {
        for (span in spans) {
            if (span.isEmpty) continue
            return span.startsWith(prefix)
        }
        return false
    }

    fun trimStart() {
        for (span in spans) {
            if (span.isEmpty) continue
            span.trimStart()
            return
        }
    }

    fun copy() = SpanCollection(Seq.with(spans))
}

internal class TemplateSpan(val source: String, var start: Int, var length: Int, val line: Int, val column: Int) {
    constructor(source: SpanCollection) : this(source.nonEmptySpan!!)
    constructor(source: StringSpan) : this(source.source, source.start, 1, source.line, source.column)
}

internal class TemplateSpanCollection {
    val spans = newSeq<TemplateSpan>()
    var predictPos = -1
    var lastString: String? = null

    fun growFrom(source: StringSpan) {
        if (source.start == predictPos && source.source === lastString) {
            spans.last().length++
            predictPos++
            return
        }
        spans.add(TemplateSpan(source))
        predictPos = source.start + 1
        lastString = source.source
    }

    fun growFrom(source: SpanCollection) {
        if (source.start == predictPos && source.source === lastString) {
            spans.last().length++
            predictPos++
            return
        }
        spans.add(TemplateSpan(source))
        predictPos = source.start + 1
        lastString = source.source
    }

    fun intoSpanCollection(): SpanCollection {
        val s = newSeq<StringSpan>(spans.size)
        for (span in spans) {
            s.add(StringSpan(span.source, span.start, span.length, span.line, span.column))
        }
        return SpanCollection(s)
    }
}

internal class StringSpan(
    var source: String,
    var start: Int,
    var length: Int,
    var line: Int,
    var column: Int,
    val initalLength: Int,
    // var columnCollection: IntSeq?,
) {
    constructor(
        source: String,
        start: Int,
        length: Int,
        line: Int,
        column: Int,
    ) : this(
        source,
        start,
        length,
        line,
        column,
        length,
        // null,
    )
    constructor(source: String) : this(source, 0, source.length, 1, 1)

    val at get() = "$line:$column"
    val isEmpty get() = length == 0

    operator fun get(pos: Int): Char {
        if (pos < 0) throw IndexOutOfBoundsException("Index is negative")
        if (pos >= length) throw IndexOutOfBoundsException("Index is too large ($pos >= $length)")
        return source[start + pos]
    }

    fun next(): Char {
        val c = get(0)
        inc()
        return c
    }

    fun inc(): Int {
        if (get(0) == '\n') {
            // if (columnCollection?.let { it.add(column); K } == null) columnCollection = IntSeq.with(column)
            line++
            column = 1
        } else column++
        length--
        start++
        return length
    }

    fun dec(): Int {
        if (length == initalLength) throw IndexOutOfBoundsException("Cannot subtract any more")

        length++
        start--

        if (get(0) == '\n') {
            line--
            // Those must always be available.
            // column = columnCollection!!.pop()
            column = Int.MAX_VALUE // This is OK since dec() is only used to counter inc() in a loop, thus this value
                                   // is immediately discarded.
        } else column--

        return length
    }

    fun stripPrefix(prefix: String): Boolean {
        if (length < prefix.length) return false
        if (!source.startsWith(prefix, start)) return false
        prefix.indices.forEach { _ -> inc() }
        return true
    }

    fun startsWith(prefix: String): Boolean {
        if (length < prefix.length) return false
        return source.startsWith(prefix, start)
    }

    fun trimStart() {
        while (!isEmpty && get(0).isWhitespace()) inc()
        return
    }

    // fun copy(): StringSpan = StringSpan(source, start, length, line, column, initalLength, columnCollection?.let { IntSeq(it.size).apply { addAll(it) } })
    fun copy(): StringSpan = StringSpan(source, start, length, line, column, initalLength)
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
    fun parse(script: String): Script = parse(StringSpan(script))
    @JvmStatic
    internal inline fun parse(script: StringSpan): Script = parse(SpanCollection(script))
    @JvmStatic
    internal inline fun parse(script: SpanCollection): Script = parseRoot(script)
}

object Tlu {
    fun <R: T, T : L<R>> list(l: T, list: Iterable<String>, key: String, sep: String = ","): T {
        l.put(key, list.joinToString(sep))
        l.put("${key}.len", list.count().toString())
        return l
    }
}
