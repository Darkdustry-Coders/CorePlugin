import kotlin.test.Test
import mindurka.build.StringPtr

class Tests {
    @Test
    fun checkIfThisShitWorks() {
        val text = "those are more args"
        val ptr = StringPtr(text)
        assert("those" == ptr.takeUntil { it == ' ' })
        ptr.trimStart()
        assert("are" == ptr.takeUntil { it == ' ' })
        ptr.trimStart()
        assert("more args" == ptr.rest())
        assert(ptr.isEmpty())
    }
}
