package app.eon.launcher.data

import org.junit.Assert.*
import org.junit.Test

class ScanPathPolicyTest {
    @Test fun `default is used only when settings have never been saved`() {
        assertEquals(listOf("/data/adb"), ScanPathPolicy.initialPaths(null))
        assertEquals(emptyList<String>(), ScanPathPolicy.initialPaths(emptySet()))
    }

    @Test fun `normalization deduplicates separators and dot components`() {
        assertEquals(listOf("/data/adb"), ScanPathPolicy.initialPaths(setOf("/data/adb/", "/data//adb/.", "/data/other/../adb")))
        assertEquals("/", ScanPathPolicy.normalize("/../../"))
    }

    @Test fun `valid filename whitespace and quote characters are preserved`() {
        assertEquals("/a ' /b\n", ScanPathPolicy.normalize("/a ' /b\n"))
    }

    @Test fun `relative and null paths are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ScanPathPolicy.normalize("data/adb") }
        assertThrows(IllegalArgumentException::class.java) { ScanPathPolicy.normalize("/a\u0000b") }
    }

    @Test fun `launch is limited to exact configured parents`() {
        val paths = listOf("/data/adb", "/data/local/tmp")
        assertTrue(ScanPathPolicy.allowsFile(paths, "/data/adb/module.so"))
        assertTrue(ScanPathPolicy.allowsFile(paths, "/data/local/tmp/module.so"))
        assertFalse(ScanPathPolicy.allowsFile(paths, "/data/adb/child/module.so"))
        assertFalse(ScanPathPolicy.allowsFile(paths, "/data/adb2/module.so"))
        assertFalse(ScanPathPolicy.allowsFile(paths, "/data/adb/module.txt"))
        assertFalse(ScanPathPolicy.allowsFile(emptyList(), "/data/adb/module.so"))
        assertTrue(ScanPathPolicy.allowsFile(listOf("/"), "/module.so"))
    }

    @Test fun `parent of root stays root`() {
        assertEquals("/", ScanPathPolicy.parent("/"))
        assertEquals("/", ScanPathPolicy.parent("/data"))
        assertEquals("/data", ScanPathPolicy.parent("/data/adb/"))
    }
}
