package app.eon.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellEscaperTest {
    @Test fun rejectsNullInLaunchArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            buildLaunchCommand("/data/adb/module.so", "/data/adb", listOf("a\u0000b"))
        }
    }

    @Test fun rejectsRelativeWorkingDirectory() {
        assertThrows(IllegalArgumentException::class.java) {
            buildLaunchCommand("/data/adb/module.so", "relative", emptyList())
        }
    }
    @Test
    fun `scan command is restricted to current directory`() {
        val command = buildScanCommand("/data/adb")

        assertTrue(command.contains("find -H '/data/adb' -maxdepth 1"))
        assertFalse(command.contains("find -L"))
        assertFalse(command.contains("-mindepth 2"))
    }

    @Test fun quotesPlainValue() {
        assertEquals("'/data/adb/module.so'", ShellEscaper.quote("/data/adb/module.so"))
    }

    @Test fun escapesSingleQuoteWithoutAllowingCommandBreakout() {
        assertEquals("'a'\\''b; id'", ShellEscaper.quote("a'b; id"))
    }

    @Test fun tokenizesQuotedArguments() {
        assertEquals(
            listOf("--config", "/data/local/a b.json", "plain value", ""),
            ArgumentTokenizer.tokenize("--config '/data/local/a b.json' plain\\ value \"\""),
        )
    }

    @Test fun rejectsUnclosedQuote() {
        assertThrows(IllegalArgumentException::class.java) { ArgumentTokenizer.tokenize("--name 'broken") }
    }

    @Test fun rejectsDanglingEscape() {
        assertThrows(IllegalArgumentException::class.java) { ArgumentTokenizer.tokenize("broken\\") }
    }
}
