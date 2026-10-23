package app.eon.launcher.data

import app.eon.launcher.model.LogLine
import app.eon.launcher.model.RootState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RootRunnerTest {
    @get:Rule val folder = TemporaryFolder()

    private fun localRunner(timeoutMillis: Long = 5000L) = RootRunner(
        startProcess = { ProcessBuilder("sh", "-c", it).start() },
        commandTimeoutMillis = timeoutMillis,
    )

    @Test(timeout = 10000)
    fun `scan reads only direct files and preserves special filenames`() = runBlocking {
        val directory = folder.newFolder("quote' and spaces")
        val executable = java.io.File(directory, "lib with ' and\nnewline.so").apply {
            writeText("fixture")
            assertTrue(setExecutable(true))
        }
        val regular = java.io.File(directory, "regular.so").apply { writeText("fixture") }
        java.io.File(directory, "ignored.txt").writeText("fixture")
        java.io.File(directory, "child").apply { mkdir() }.resolve("hidden.so").writeText("fixture")

        val files = localRunner().scan(directory.absolutePath)

        assertEquals(setOf(executable.absolutePath, regular.absolutePath), files.map { it.path }.toSet())
        assertTrue(files.single { it.path == executable.absolutePath }.executable)
        assertFalse(files.single { it.path == regular.absolutePath }.executable)
    }

    @Test(timeout = 10000)
    fun `root authorization timeout kills its process`() = runBlocking {
        var process: Process? = null
        val runner = RootRunner(startProcess = {
            ProcessBuilder("sh", "-c", "while :; do :; done").start().also { process = it }
        }, commandTimeoutMillis = 100L)

        assertEquals(RootState.DENIED, runner.checkRoot())
        val finished = requireNotNull(process)
        assertTrue(finished.waitFor(2, TimeUnit.SECONDS))
        assertFalse(finished.isAlive)
    }

    @Test(timeout = 10000)
    fun `stdin secrets are redacted on both streams and wiped on exit`() = runBlocking {
        val secret = "test ' 密钥".toCharArray()
        val lines = Collections.synchronizedList(mutableListOf<Pair<LogLine.Stream, String>>())
        val code = localRunner().launch(
            "/bin/sh", folder.root.absolutePath,
            listOf("-c", "IFS= read -r key; printf '%s\\n' \"\$key\"; printf '%s\\n' \"\$key\" >&2"),
            secret,
        ) { stream, text -> lines += stream to text }

        assertEquals(0, code)
        assertEquals(setOf(LogLine.Stream.STDOUT, LogLine.Stream.STDERR), lines.map { it.first }.toSet())
        assertTrue(lines.all { it.second == "••••" })
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test(timeout = 10000)
    fun `stdout and stderr can both exceed pipe capacity`() = runBlocking {
        val lines = Collections.synchronizedList(mutableListOf<Pair<LogLine.Stream, String>>())
        val payload = "x".repeat(100)
        val script = "read key; i=0; while [ \"\$i\" -lt 1000 ]; do printf '%s\\n' '$payload'; printf '%s\\n' '$payload' >&2; i=\$((i+1)); done; exit 7"
        val code = localRunner().launch("/bin/sh", folder.root.absolutePath, listOf("-c", script), "secret".toCharArray()) {
            stream, text -> lines += stream to text
        }
        assertEquals(7, code)
        assertEquals(1000, lines.count { it.first == LogLine.Stream.STDOUT })
        assertEquals(1000, lines.count { it.first == LogLine.Stream.STDERR })
    }

    @Test(timeout = 10000)
    fun `missing scan directory reports failure instead of an empty list`() = runBlocking {
        try {
            localRunner().scan(folder.root.resolve("missing").absolutePath)
            fail("Missing directory must be reported")
        } catch (error: IllegalStateException) {
            assertFalse(error.message.isNullOrEmpty())
        }
    }

    @Test(timeout = 10000)
    fun `cancel destroys process and releases the launch slot`() = runBlocking {
        var process: Process? = null
        val runner = RootRunner(startProcess = {
            ProcessBuilder("sh", "-c", it).start().also { process = it }
        })
        val started = CompletableDeferred<Unit>()
        val secret = "secret".toCharArray()
        val job = launch {
            runner.launch("/bin/sh", folder.root.absolutePath, listOf("-c", "while :; do :; done"), secret,
                onStarted = { started.complete(Unit) }) { _, _ -> }
        }
        withTimeout(2000L) { started.await() }
        val other = "other".toCharArray()
        try {
            runner.launch("/bin/true", folder.root.absolutePath, emptyList(), other) { _, _ -> }
            fail("Concurrent launch must be rejected")
        } catch (_: IllegalStateException) {
            assertTrue(other.all { it == '\u0000' })
        }
        withTimeout(3000L) { job.cancelAndJoin() }
        assertTrue(process!!.waitFor(2, TimeUnit.SECONDS))
        assertFalse(runner.isRunning())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test(timeout = 10000)
    fun `cancellation during process creation does not leak a process`() = runBlocking {
        val created = CountDownLatch(1)
        val release = CountDownLatch(1)
        var process: Process? = null
        val runner = RootRunner(startProcess = {
            ProcessBuilder("sh", "-c", "while :; do :; done").start().also {
                process = it
                created.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
        })
        val secret = "secret".toCharArray()
        val job = launch { runner.launch("/bin/true", folder.root.absolutePath, emptyList(), secret) { _, _ -> } }
        withTimeout(2000L) { while (created.count != 0L) delay(5) }
        job.cancel()
        release.countDown()
        withTimeout(3000L) { job.join() }
        assertTrue(process!!.waitFor(2, TimeUnit.SECONDS))
        assertFalse(runner.isRunning())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun `creation failure wipes secret and never calls started`() = runBlocking {
        val secret = "secret".toCharArray()
        var started = false
        val runner = RootRunner(startProcess = { throw java.io.IOException("fixture") })
        try {
            runner.launch("/bin/true", "/", emptyList(), secret, onStarted = { started = true }) { _, _ -> }
            fail("Expected process creation failure")
        } catch (_: java.io.IOException) {
            assertFalse(started)
            assertFalse(runner.isRunning())
            assertTrue(secret.all { it == '\u0000' })
        }
    }
}
