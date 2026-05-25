import arc.util.io.Streams
import buj.tl.LocaleFile
import buj.tl.StringSpan
import buj.tl.TemplateSpanCollection
import buj.tl.Tl
import mindurka.util.unreachable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TlTest {
    @Test
    fun spanCanAdd() {
        val span = StringSpan("ab\nc")
        span.inc()
        span.inc()
        span.inc()
        span.inc()

        assertEquals(4, span.start)
        assertEquals(0, span.length)
        assertEquals(2, span.line)
        assertEquals(2, span.column)
    }

    @Test
    fun spanStripPrefix() {
        val span = StringSpan("aaa bbb ccc")

        assertTrue(span.stripPrefix("aaa"))
        assertFalse(span.stripPrefix("bbb"))
        assertTrue(span.stripPrefix(" "))
        assertTrue(span.stripPrefix("bbb"))
    }

    @Test
    fun spanCollection() {
        val col = TemplateSpanCollection()
        val span = StringSpan("aaa bbb\nccc")

        while (!span.isEmpty) {
            if (!span[0].isWhitespace()) col.growFrom(span)
            span.inc()
        }

        assertEquals(0, col.spans[0].start)
        assertEquals(1, col.spans[0].line)
        assertEquals(4, col.spans[1].start)
        assertEquals(1, col.spans[1].line)
        assertEquals(8, col.spans[2].start)
        assertEquals(2, col.spans[2].line)

        val built = col.intoSpanCollection()

        assertEquals(0, built.spans[0].start)
        assertEquals(1, built.spans[0].line)
        assertEquals(4, built.spans[1].start)
        assertEquals(1, built.spans[1].line)
        assertEquals(8, built.spans[2].start)
        assertEquals(2, built.spans[2].line)
    }

    @Test
    fun tlParser() {
        val script = Tl.parse("hello {b}")
        assertEquals("Combo(\"hello \", Key(\"b\"))", script.debug())
    }

    @Test
    fun parseUnicode() {
        val script = Tl.parse("\\u{E837}")
        assertEquals("\"\uE837\"", script.debug())
    }

    @Test
    fun kvParser() {
        val localeFile = LocaleFile.parse(Streams.copyString(javaClass.classLoader.getResourceAsStream("kvtest.l")))
        val a = assertNotNull(localeFile["a"])
        assertEquals("Combo(\"hello \", Key(\"b\"))", a.debug())
    }

    @Test
    fun correctErrorLocation() {
        try {
            LocaleFile.parse(Streams.copyString(javaClass.classLoader.getResourceAsStream("errtest.l")))
            unreachable("File had no errors")
        } catch (e: Exception) {
            assertEquals("unenclosed key at 3:7", e.message)
        }
    }
}