package app.eon.launcher.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DirectoryScannerTest {
    @get:Rule val folder = TemporaryFolder()
    private fun runner() = RootRunner(startProcess = { ProcessBuilder("sh", "-c", it).start() })

    @Test(timeout = 10000)
    fun `merge keeps same names in different directories and survives a missing directory`() = runBlocking {
        val first = folder.newFolder("first")
        val second = folder.newFolder("second")
        first.resolve("module.so").writeText("fixture")
        second.resolve("module.so").writeText("fixture")
        val missing = folder.root.resolve("missing").absolutePath
        val result = DirectoryScanner(runner()).scan(listOf(first.absolutePath, missing, second.absolutePath))
        assertEquals(setOf(first.resolve("module.so").absolutePath, second.resolve("module.so").absolutePath), result.files.map { it.path }.toSet())
        assertEquals(setOf(missing), result.errors.keys)
    }

    @Test(timeout = 10000)
    fun `directory aliases scan only once`() = runBlocking {
        val directory = folder.newFolder("original")
        directory.resolve("module.so").writeText("fixture")
        val alias = folder.root.resolve("alias")
        Files.createSymbolicLink(alias.toPath(), directory.toPath())
        val result = DirectoryScanner(runner()).scan(listOf(directory.absolutePath, alias.absolutePath))
        assertEquals(1, result.files.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test(timeout = 10000)
    fun `browser returns canonical directory and direct folders only`() = runBlocking {
        val directory = folder.newFolder("space ' parent")
        val child = directory.resolve("space ' child\n").apply { mkdir() }
        child.resolve("nested").mkdir()
        directory.resolve("file.so").writeText("fixture")
        val alias = folder.root.resolve("alias")
        Files.createSymbolicLink(alias.toPath(), directory.toPath())
        val listing = runner().browseDirectory(alias.absolutePath)
        assertEquals(directory.canonicalPath, listing.path)
        assertEquals(listOf(child.absolutePath), listing.children)
    }

    @Test(timeout = 10000)
    fun `empty directory can be selected and scanned`() = runBlocking {
        val directory = folder.newFolder("empty")
        assertTrue(runner().browseDirectory(directory.absolutePath).children.isEmpty())
        val result = DirectoryScanner(runner()).scan(listOf(directory.absolutePath))
        assertTrue(result.files.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test(timeout = 10000)
    fun `one directory timeout does not cancel the remaining scan`() = runBlocking {
        val directory = folder.newFolder("valid")
        directory.resolve("module.so").writeText("fixture")
        val hanging = folder.root.resolve("hanging").absolutePath
        val runner = RootRunner(startProcess = { command ->
            ProcessBuilder("sh", "-c", if (command.contains(hanging)) "while :; do :; done" else command).start()
        }, commandTimeoutMillis = 100L)
        val result = DirectoryScanner(runner).scan(listOf(hanging, directory.absolutePath))
        assertEquals(setOf(hanging), result.errors.keys)
        assertEquals(1, result.files.size)
    }

    @Test(timeout = 10000)
    fun `user cancellation propagates and stops directory reading`() = runBlocking {
        var process: Process? = null
        val runner = RootRunner(startProcess = {
            ProcessBuilder("sh", "-c", "while :; do :; done").start().also { process = it }
        })
        try {
            withTimeout(200L) { DirectoryScanner(runner).scan(listOf("/fixture")) }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(requireNotNull(process).waitFor(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 10000)
    fun `file execution check rejects a replaced symlink or removed file`() = runBlocking {
        val file = folder.newFile("module.so")
        assertFalse(runner().isExecutableFile(file.absolutePath))
        assertTrue(file.setExecutable(true))
        assertTrue(runner().isExecutableFile(file.absolutePath))
        val alias = folder.root.resolve("alias.so")
        Files.createSymbolicLink(alias.toPath(), file.toPath())
        assertFalse(runner().isExecutableFile(alias.absolutePath))
        assertTrue(file.delete())
        assertFalse(runner().isExecutableFile(file.absolutePath))
    }
}
