package buj.srt

import arc.struct.Seq

class SrtModule {
    val safety = DefaultSafetyPolicy

    companion object {
        /**
         * Parse code into a module.
         */
        @JvmStatic
        fun parse(code: String): SrtModule {
            TODO()
        }
    }
}

val DefaultSafetyPolicy = SrtSafety().freeze()

/**
 * Safety policy of a runtime.
 */
class SrtSafety {
    /**
     * Whether to disable unsafe operations in current context.
     */
    var userCode = false
        set(value) {
            if (frozen) throw IllegalStateException("Attempting to change a frozen policy")
            field = value
        }

    /**
     * Whether this policy should be cloned before modifying.
     */
    var frozen = false

    fun userCode(): SrtSafety {
        val safety = if (frozen) clone() else this
        safety.userCode = true
        return safety
    }

    fun freeze(): SrtSafety {
        frozen = true
        return this
    }

    fun clone() = SrtSafety().apply {
        userCode = this@SrtSafety.userCode
    }
}

data class SrtError(val name: String, val message: String, val stack: Seq<String>) {
    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("$name: $message\n\n")
        for (x in stack) builder.append("at $x\n")
        return builder.toString()
    }
}

/**
 * Srt result type.
 */
interface SrtResult {
    /**
     * Amount of ticks that were used since last call.
     */
    val usedTicks: Int

    /**
     * Return value of the script that was evaluated.
     */
    data class Value(override val usedTicks: Int, val value: Any): SrtResult
    /**
     * Runtime has thrown an error.
     */
    data class Error(override val usedTicks: Int, val error: SrtError): SrtResult
    /**
     * JVM has encountered an error.
     */
    data class JvmError(override val usedTicks: Int, val error: Throwable): SrtResult
    /**
     * Runtime has run over its resource or runtime limit.
     */
    data class WouldBlock(override val usedTicks: Int): SrtResult
}

/**
 * Safe scripting runtime.
 */
class Srt {
    /** Runtime safety policy. */
    val safety = DefaultSafetyPolicy

    /** Unpause runtime. */
    fun tick(maxOps: Int, maxTime: Float): SrtResult { TODO() }

    /** Evaluate code. */
    fun eval(maxOps: Int, maxTime: Float, code: String): SrtResult { TODO() }
}