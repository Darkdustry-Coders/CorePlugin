package mindurka.util

class StringRead(val source: String) : mindurka.util.Read<FormatException> {
    private var seek = 0

    @Throws(FormatException::class)
    override fun i(): Int {
        if (seek >= source.length) throw FormatException("No more data is available")
        var length = source.indexOf(",", seek) - seek
        if (length < 0) length = source.length - seek
        if (length == 0) throw FormatException("Cannot interpret nil as an int")
        if (length == 1 && source[seek] == '0') {
            seek += 2
            return 0
        }
        val `val` = arc.util.Strings.parseInt(source, 10, 0, seek, seek + length)
        if (`val` == 0) throw FormatException("Not an integer")
        seek += length + 1
        return `val`
    }

    @Throws(FormatException::class)
    override fun l(): Long {
        if (seek >= source.length) throw FormatException("No more data is available")
        var length = source.indexOf(",", seek) - seek
        if (length < 0) length = source.length - seek
        if (length == 0) throw FormatException("Cannot interpret nil as a long")
        if (length == 1 && source[seek] == '0') {
            seek += 2
            return 0
        }
        val `val` = arc.util.Strings.parseLong(source, 10, 0, seek, (seek + length).toLong())
        if (`val` == 0L) throw FormatException("Not an integer")
        seek += length + 1
        return `val`
    }

    @Throws(FormatException::class)
    override fun f(): Float {
        if (seek >= source.length) throw FormatException("No more data is available")
        var length = source.indexOf(",", seek) - seek
        if (length < 0) length = source.length - seek
        if (length == 0) throw FormatException("Cannot interpret nil as a float")
        if (length == 1 && source[seek] == '0') return 0f
        val substr = source.substring(seek, seek + length)
        if (!arc.util.Strings.canParseFloat(substr)) throw FormatException("Not a float")
        seek += length + 1
        return arc.util.Strings.parseFloat(substr)
    }

    @Throws(FormatException::class)
    override fun sym(): String {
        if (seek >= source.length) throw FormatException("No more data is available")
        var length = source.indexOf(",", seek) - seek
        if (length < 0) length = source.length - seek
        if (length == 0) throw FormatException("Cannot interpret nil as a symbol")
        val s = source.substring(seek, seek + length)
        seek += length + 1
        return s
    }

    @Throws(FormatException::class)
    override fun nil(): Boolean {
        val nil = seek >= source.length || source[seek] == ','
        if (nil) seek++
        return nil
    }
}
