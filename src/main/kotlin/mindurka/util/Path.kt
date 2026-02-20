package mindurka.util

import arc.files.Fi

@JvmInline
value class SafeFilename(val self: String) {
    companion object {
        fun create(path: String): SafeFilename? =
            try {
                SafeFilename(path)
            } catch (_: Exception) {
                null
            }
    }

    init {
        require(Regex("^[a-zA-Z0-9_-]{1,64}$").matches(self))
    }

    override fun toString(): String = self
}

fun Fi.child(child: SafeFilename): Fi = child(child.toString())
