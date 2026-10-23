package app.eon.launcher.data

import app.eon.launcher.model.LogLine
import app.eon.launcher.model.RootState
import app.eon.launcher.model.SoFile
import app.eon.launcher.model.DirectoryListing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.charset.StandardCharsets

class RootRunner(
    private val startProcess: (String) -> Process = { ProcessBuilder("su", "-c", it).start() },
    private val commandTimeoutMillis: Long = 15_000L,
) {
    private val launchMutex = Mutex()

    suspend fun checkRoot(): RootState = try {
        val result = command("id -u")
        if (result.code == 0 && result.output.toString(StandardCharsets.UTF_8).trim() == "0") {
            RootState.READY
        } else {
            RootState.DENIED
        }
    } catch (_: CommandTimeoutException) {
        RootState.DENIED
    } catch (_: IOException) {
        RootState.UNAVAILABLE
    } catch (_: SecurityException) {
        RootState.UNAVAILABLE
    }

    suspend fun scan(directory: String): List<SoFile> {
        require(directory.startsWith('/')) { "扫描目录必须是绝对路径" }
        val result = try {
            command(buildScanCommand(directory))
        } catch (_: CommandTimeoutException) {
            error("扫描超时")
        }
        check(result.code == 0) { result.error.ifBlank { "扫描命令退出码：${result.code}" } }
        return parseScanOutput(result.output).sortedBy { it.name.lowercase() }
    }

    suspend fun resolveDirectory(path: String): String = readDirectory(path, children = false).path

    suspend fun browseDirectory(path: String): DirectoryListing = readDirectory(path, children = true)

    private suspend fun readDirectory(path: String, children: Boolean): DirectoryListing {
        val normalized = ScanPathPolicy.normalize(path)
        val listing = if (children) " && find . -mindepth 1 -maxdepth 1 -type d -print0" else ""
        val result = try {
            command("cd -P ${ShellEscaper.quote(normalized)} && test -r . && test -x . && printf '%s\\000' \"\$PWD\"$listing")
        } catch (timeout: CommandTimeoutException) {
            throw timeout
        } catch (failure: IOException) {
            throw IOException("无法读取目录，请确认设备提供 su 并已授予 Root 权限", failure)
        } catch (failure: SecurityException) {
            throw IOException("无法读取目录，请检查 Root 授权", failure)
        }
        check(result.code == 0) { "无法访问此目录，请检查目录是否存在及 Root 授权" }
        val fields = result.output.toString(StandardCharsets.UTF_8).split('\u0000').dropLastWhile { it.isEmpty() }
        check(fields.isNotEmpty()) { "目录读取失败" }
        val canonical = ScanPathPolicy.normalize(fields.first())
        return DirectoryListing(canonical, fields.drop(1).map { child ->
            ScanPathPolicy.normalize("$canonical/${child.removePrefix("./")}")
        }.sortedBy { it.substringAfterLast('/').lowercase() })
    }

    suspend fun isExecutableFile(path: String): Boolean {
        val quoted = ShellEscaper.quote(ScanPathPolicy.normalize(path))
        return command("test -f $quoted && test -x $quoted && ! test -L $quoted").code == 0
    }

    suspend fun launch(
        path: String,
        workingDirectory: String,
        arguments: List<String>,
        secret: CharArray,
        onStarted: suspend () -> Unit = {},
        onLine: (LogLine.Stream, String) -> Unit,
    ): Int {
        try {
            check(launchMutex.tryLock()) { "已有 SO 正在运行" }
            try {
                return withProcess(buildLaunchCommand(path, workingDirectory, arguments)) { process ->
                    val stdout = async { drain(process.inputStream.bufferedReader(), LogLine.Stream.STDOUT, secret, onLine) }
                    val stderr = async { drain(process.errorStream.bufferedReader(), LogLine.Stream.STDERR, secret, onLine) }
                    onStarted()
                    val input = async {
                        runInterruptible {
                            process.outputStream.writer(StandardCharsets.UTF_8).use {
                                it.write(secret)
                                it.write('\n'.code)
                            }
                        }
                    }
                    val code = runInterruptible { process.waitFor() }
                    input.await()
                    stdout.await()
                    stderr.await()
                    code
                }
            } finally {
                launchMutex.unlock()
            }
        } finally {
            secret.fill('\u0000')
        }
    }

    fun isRunning(): Boolean = launchMutex.isLocked

    private suspend fun command(command: String): CommandResult = withTimeoutOrNull(commandTimeoutMillis) {
        withProcess(command) { process ->
            process.outputStream.close()
            val stdout = async { runInterruptible { readBounded(process.inputStream) } }
            val stderr = async { runInterruptible { readBounded(process.errorStream) } }
            val code = runInterruptible { process.waitFor() }
            CommandResult(stdout.await(), stderr.await().toString(StandardCharsets.UTF_8).trim(), code)
        }
    } ?: throw CommandTimeoutException()

    /** One owner for process creation, cancellation, pipe lifetime and cleanup. */
    private suspend fun <T> withProcess(command: String, block: suspend CoroutineScope.(Process) -> T): T =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val process = startProcess(command)
            try {
                coroutineScope {
                    try {
                        currentCoroutineContext().ensureActive()
                        block(process)
                    } finally {
                        // Kill before coroutineScope joins potentially blocked pipe readers.
                        if (process.isAlive) process.destroyForcibly()
                    }
                }
            } finally {
                closeProcess(process)
            }
        }

    private suspend fun drain(
        reader: java.io.Reader,
        stream: LogLine.Stream,
        secret: CharArray,
        onLine: (LogLine.Stream, String) -> Unit,
    ) = runInterruptible {
        reader.use { LogPolicy.readLines(it, secret) { text -> onLine(stream, text) } }
    }

    private fun closeProcess(process: Process) {
        if (process.isAlive) process.destroyForcibly()
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            check(output.size() + count <= 1024 * 1024) { "命令输出过长" }
            output.write(buffer, 0, count)
        }
    }

    private data class CommandResult(val output: ByteArray, val error: String, val code: Int)
    private class CommandTimeoutException : IOException("Root 命令超时")
}

internal fun buildLaunchCommand(path: String, workingDirectory: String, arguments: List<String>): String {
    require(path.startsWith('/')) { "目标文件必须使用绝对路径" }
    require(workingDirectory.startsWith('/')) { "工作目录必须使用绝对路径" }
    require((listOf(path, workingDirectory) + arguments).none { '\u0000' in it }) { "路径或参数包含无效字符" }
    return "cd ${ShellEscaper.quote(workingDirectory)} && exec " +
        (listOf(path) + arguments).joinToString(" ", transform = ShellEscaper::quote)
}

internal fun buildScanCommand(directory: String): String {
    val inspect = "for path do if [ -x \"\$path\" ]; then printf '1\\000%s\\000' \"\$path\"; else printf '0\\000%s\\000' \"\$path\"; fi; done"
    return "find ${ShellEscaper.quote(directory)} -maxdepth 1 -type f -name '*.so' -exec sh -c ${ShellEscaper.quote(inspect)} sh {} +"
}

internal fun parseScanOutput(bytes: ByteArray): List<SoFile> {
    val fields = bytes.toString(StandardCharsets.UTF_8).split('\u0000').dropLastWhile { it.isEmpty() }
    check(fields.size % 2 == 0) { "扫描结果格式错误" }
    return fields.chunked(2).map { (executable, path) ->
        check(executable == "0" || executable == "1") { "扫描结果格式错误" }
        SoFile(path = path, executable = executable == "1")
    }
}
