package app.eon.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LogPolicyTest {
    @Test fun `line endings and final unterminated line are handled`() {
        val lines = mutableListOf<String>()
        LogPolicy.readLines("a\r\nb\rc\n\nd".reader(), charArrayOf(), lines::add)
        assertEquals(listOf("a", "b", "c", "", "d"), lines)
    }

    @Test fun `oversized line is bounded and boundary secret remains redacted`() {
        val prefix = "x".repeat(LogPolicy.MAX_LINE_LENGTH - 2)
        val lines = mutableListOf<String>()
        LogPolicy.readLines((prefix + "sensitive" + "z".repeat(100000) + "\nnext").reader(), "sensitive".toCharArray(), lines::add)
        assertEquals(prefix + "••…", lines.first())
        assertEquals("next", lines.last())
    }
    @Test
    fun `redacts every occurrence of a secret`() {
        val secret = "p@ss word".toCharArray()

        assertEquals("token=••••; repeated=••••", LogPolicy.redact("token=p@ss word; repeated=p@ss word", secret))
    }

    @Test
    fun `empty secret does not alter output`() {
        assertEquals("normal output", LogPolicy.redact("normal output", charArrayOf()))
    }

    @Test
    fun `bounded log keeps newest entries`() {
        val result = listOf(1, 2, 3).fold(emptyList<Int>()) { lines, value ->
            LogPolicy.appendBounded(lines, value, capacity = 2)
        }

        assertEquals(listOf(2, 3), result)
    }
}
