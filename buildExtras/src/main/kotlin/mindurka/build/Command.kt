package mindurka.build

enum class CommandType {
    /** A console command. */
    Console,
    /** A player command. */
    Player,
}

data class StringPtr (
    val source: String,
    var index: Int = 0,
) {
    fun isEmpty(): Boolean = source.length == index
    fun clone(): StringPtr = StringPtr(source, index)
    fun peek(): Char? = if (isEmpty()) null else source[index]
    fun rest(): String? {
        if (isEmpty()) return null
        val idx = index
        index = source.length
        return source.substring(idx)
    }
    fun trimStart() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
    fun takeUntil(f: (Char) -> Boolean): String? {
        if (isEmpty()) return null
        if (f(source[index])) return null

        for (idx in index + 1..<source.length) {
            if (f(source[idx])) {
                val s = source.substring(index, idx)
                index = idx
                return s
            }
        }

        val idx = index
        index = source.length
        return source.substring(idx)
    }
}

abstract class CommandImpl {
    /** Docstring to use in console. */
    abstract val doc: String
    /** Subcommand path. */
    abstract val command: Array<String>
    /** Whether command is visible in /help. Ignored for console commands. */
    abstract val hidden: Boolean
    /** Type of command. */
    abstract val type: CommandType
    /** Order by which matched command implementations take priority. Higher the better. */
    abstract val priority: Array<Int>
    /** Prepare command for execution. */
    abstract fun parse(caller: Any?, raw: String): (() -> Unit)?
}
