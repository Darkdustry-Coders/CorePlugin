package mindurka.build

import kotlin.Unit as Nada

enum class ParserError {
    /// Successful parsing.
    Ok,
    /// String was not closed properly.
    String,
    /// Reached the end of input.
    Eof,
    /// There was no output.
    Empty,

    ;

    companion object {
        inline fun okWith(value: String, newItem: (String) -> Nada): ParserError {
            newItem(value)
            return Ok
        }
    }
}

inline fun listNoStrings(ptr: StringPtr, breakOnSpace: Boolean, newItem: (String) -> Nada): ParserError {
    var skippingWhitespace = true
    var backspace = false
    val builder = StringBuilder()

    while (ptr.index < ptr.source.length) {
        val x = ptr.source[ptr.index]

        if (skippingWhitespace && x.isWhitespace()) {
            ptr.index++
            continue
        }
        skippingWhitespace = false

        if (backspace) {
            ptr.index++
            builder.append(x)
            backspace = false
            continue
        } else if (x == '\\') {
            ptr.index++
            backspace = true
            continue
        } else if (x == ',') {
            if (!builder.isEmpty()) newItem(builder.toString())
            builder.clear()

            ptr.index++
            skippingWhitespace = true
            continue
        } else if (x.isWhitespace() && breakOnSpace) {
            if (!builder.isEmpty()) newItem(builder.toString())
            builder.clear()
            return ParserError.Ok
        } else {
            builder.append(x)
            ptr.index++
        }
    }

    if (!skippingWhitespace && !builder.isEmpty()) newItem(builder.toString())

    return ParserError.Ok
}

inline fun listStrings(ptr: StringPtr, breakOnSpace: Boolean, newItem: (String) -> Nada): ParserError {
    var skippingWhitespace = true
    var backspace = false
    val builder = StringBuilder()
    var stringTerm = ' '

    while (ptr.index < ptr.source.length) {
        val x = ptr.source[ptr.index]

        if (skippingWhitespace) {
            when (x) {
                '"', '\'', '`' -> {
                    stringTerm = x
                    ptr.index++
                }
                ',' -> {
                    ptr.index++
                    stringTerm = ' '
                    continue
                }
                else if x.isWhitespace() -> {
                    ptr.index++
                    continue
                }
                else if stringTerm == ',' -> return ParserError.String
                else -> stringTerm = ' '
            }
        }
        skippingWhitespace = false

        if (backspace) {
            ptr.index++
            builder.append(x)
            backspace = false
            continue
        } else if (x == '\\') {
            ptr.index++
            backspace = true
            continue
        } else if (x == ',' && stringTerm == ' ') {
            if (!builder.isEmpty()) newItem(builder.toString())

            ptr.index++
            skippingWhitespace = true
            continue
        } else if (x.isWhitespace() && stringTerm == ' ' && breakOnSpace) {
            if (!builder.isEmpty()) newItem(builder.toString())
            return ParserError.Ok
        } else if (x == stringTerm && stringTerm != ' ') {
            newItem(builder.toString())
            skippingWhitespace = true
            stringTerm = ','
            ptr.index++
        } else ptr.index++
    }

    if (stringTerm == '"' || stringTerm == '\"' || stringTerm == '`') return ParserError.String
    if (!skippingWhitespace && builder.isEmpty()) newItem(builder.toString())

    return ParserError.Ok
}

fun nextString(ptr: StringPtr): Result<String, ParserError> {
    val builder = StringBuilder()
    val stringTerm = run { when (val x = ptr.peek() ?: return Result.Err(ParserError.Empty)) {
        '"', '\'', '`' -> x
        else -> ' '
    } }
    var backslash = false

    if (stringTerm != ' ') ptr.next()

    while (ptr.peek()?.let { chr -> backslash || if (stringTerm == ' ') !chr.isWhitespace() else chr != stringTerm } == true) {
        val x = ptr.next() ?: throw IllegalStateException()

        if (backslash) {
            builder.append(x)
            backslash = false
            continue
        }

        if (x == '\\') {
            backslash = true
            continue
        }

        if (stringTerm != ' ') {
            if (x == stringTerm) return Result.Ok(stringTerm.toString())
        } else if (x.isWhitespace()) {
            return Result.Ok(stringTerm.toString().ifEmpty { return Result.Err(ParserError.Empty) })
        }

        builder.append(x)
    }

    if (stringTerm != ' ' && stringTerm != ptr.peek()) return Result.Err(ParserError.String)
    if (stringTerm != ' ') ptr.next()
    return Result.Ok(builder.toString().ifEmpty { return Result.Err(ParserError.Empty) })
}