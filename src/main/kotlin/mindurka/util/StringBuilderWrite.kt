package mindurka.util

data class StringBuilderWrite(val builder: java.lang.StringBuilder) : Write<NeverException?> {
    override fun i(value: kotlin.Int) {
        builder.append(value).append(',')
    }

    override fun l(value: kotlin.Long) {
        builder.append(value).append(',')
    }

    override fun f(value: kotlin.Float) {
        builder.append(value).append(',')
    }

    override fun sym(value: kotlin.String?) {
        builder.append(value).append(',')
    }

    override fun nil() {
        builder.append(',')
    }
}
