package app.eon.launcher

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.eon.launcher.data.ArgumentTokenizer
import app.eon.launcher.data.buildLaunchCommand
import app.eon.launcher.data.KeyVault
import app.eon.launcher.data.LogPolicy
import app.eon.launcher.data.RootRunner
import app.eon.launcher.model.EonUiState
import app.eon.launcher.model.LogLine
import app.eon.launcher.model.ProcessStatus
import app.eon.launcher.model.RootState
import app.eon.launcher.model.RunState
import app.eon.launcher.model.SoFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EonViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("eon_settings", Context.MODE_PRIVATE)
    private val vault = KeyVault(application)
    private val runner = RootRunner()
    private var launchJob: Job? = null
    private var rootCheckJob: Job? = null
    private val _state = MutableStateFlow(
        EonUiState(
            scanDirectory = SCAN_DIRECTORY,
        ),
    )
    val state: StateFlow<EonUiState> = _state.asStateFlow()

    init {
        refreshKeys()
        checkRoot()
    }

    fun checkRoot() {
        if (rootCheckJob?.isActive == true) return
        _state.update { it.copy(rootState = RootState.CHECKING) }
        rootCheckJob = viewModelScope.launch {
            runCatchingCancellable { runner.checkRoot() }
                .onSuccess { rootState ->
                    _state.update { it.copy(rootState = rootState) }
                    if (rootState == RootState.READY) scan()
                }
                .onFailure { error ->
                    _state.update { it.copy(rootState = RootState.UNAVAILABLE, message = error.message ?: "Root 检查失败") }
                }
        }
    }

    fun scan() {
        if (_state.value.isScanning) return
        _state.update { it.copy(isScanning = true, message = null) }
        viewModelScope.launch {
            runCatchingCancellable { runner.scan(SCAN_DIRECTORY) }
                .onSuccess { files -> _state.update { it.copy(files = mergeRunMetadata(files), isScanning = false) } }
                .onFailure { error ->
                    _state.update { it.copy(isScanning = false, message = error.message ?: "扫描失败") }
                }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun saveKey(id: String?, name: String, secret: CharArray) {
        val job = viewModelScope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { vault.save(id, name, secret) } }
                .onSuccess { refreshKeys(); _state.update { it.copy(message = "密钥已加密保存") } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "密钥保存失败") } }
        }
        job.invokeOnCompletion { secret.fill('\u0000') }
    }

    fun deleteKey(id: String) {
        viewModelScope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { vault.delete(id) } }
                .onSuccess { refreshKeys(); _state.update { it.copy(message = "密钥已删除") } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "删除失败") } }
        }
    }

    suspend fun readKey(id: String): CharArray? = try {
        // Retain ownership until withContext returns, including its cancellation boundary.
        var secret: CharArray? = null
        try {
            withContext(Dispatchers.IO) { secret = vault.read(id) }
            secret.also { if (it == null) _state.update { state -> state.copy(message = "密钥已不存在") } }
        } catch (error: Exception) {
            secret?.fill('\u0000')
            throw error
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        _state.update { it.copy(message = "无法读取此密钥，请重新输入或在密钥库中编辑") }
        null
    }

    fun lastKeyId(path: String): String? = preferences.getString("last_key:$path", null)

    fun launch(
        file: SoFile,
        secret: CharArray,
        selectedKeyId: String?,
        arguments: String,
        workingDirectory: String,
    ): Boolean {
        if (launchJob?.isCompleted == false || runner.isRunning()) {
            _state.update { it.copy(message = "已有 SO 正在运行，请先停止当前进程") }
            secret.fill('\u0000')
            return false
        }
        if (secret.isEmpty()) {
            _state.update { it.copy(message = "请输入运行密钥") }
            secret.fill('\u0000')
            return false
        }
        if (!file.executable) {
            _state.update { it.copy(message = "目标文件没有执行权限，EON 不会自动修改权限") }
            secret.fill('\u0000')
            return false
        }
        if (file.directory != SCAN_DIRECTORY || !file.name.endsWith(".so")) {
            _state.update { it.copy(message = "目标文件不在 /data/adb 当前目录") }
            secret.fill('\u0000')
            return false
        }
        val tokens = runCatching {
            require(secret.none { it == '\n' || it == '\r' || it == '\u0000' }) { "密钥不能包含换行或空字符" }
            ArgumentTokenizer.tokenize(arguments).also { buildLaunchCommand(file.path, workingDirectory, it) }
        }.getOrElse {
            _state.update { state -> state.copy(message = it.message ?: "启动参数格式错误") }
            secret.fill('\u0000')
            return false
        }
        _state.update {
            it.copy(
                run = RunState(
                    status = ProcessStatus.STARTING,
                    fileName = file.name,
                    startedAt = System.currentTimeMillis(),
                    lines = listOf(systemLine("正在启动 ${file.name}")),
                ),
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                runCatchingCancellable {
                    runner.launch(
                        file.path, workingDirectory, tokens, secret,
                        onStarted = {
                            rememberLaunchKey(file.path, selectedKeyId, secret)
                            _state.update { it.copy(run = it.run.copy(status = ProcessStatus.RUNNING)) }
                        },
                    ) { stream, text ->
                        appendLog(LogLine(System.currentTimeMillis(), stream, text))
                    }
                }.onSuccess { code ->
                    rememberRun(file.path, code)
                    appendLog(systemLine("进程已结束，退出码 $code"))
                    _state.update { it.copy(run = it.run.copy(status = ProcessStatus.FINISHED, exitCode = code)) }
                }.onFailure { error ->
                    appendLog(systemLine("启动失败：${error.message ?: "未知错误"}"))
                    _state.update { it.copy(run = it.run.copy(status = ProcessStatus.FAILED)) }
                }
            } catch (cancelled: CancellationException) {
                if (_state.value.run.status == ProcessStatus.STOPPING) {
                    _state.update { it.copy(run = it.run.copy(status = ProcessStatus.FINISHED)) }
                }
                throw cancelled
            }
        }
        launchJob = job
        job.invokeOnCompletion { secret.fill('\u0000') }
        job.start()
        return true
    }

    fun stop() {
        val job = launchJob
        if (job?.isActive != true && !runner.isRunning()) return
        _state.update { it.copy(run = it.run.copy(status = ProcessStatus.STOPPING)) }
        job?.cancel()
        appendLog(systemLine("已请求停止进程"))
    }

    fun clearLogs() {
        if (launchJob?.isCompleted == false || runner.isRunning()) return
        _state.update { it.copy(run = RunState()) }
    }

    private suspend fun rememberLaunchKey(path: String, selectedKeyId: String?, secret: CharArray) {
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val result = if (selectedKeyId != null && vault.contains(selectedKeyId)) {
                    KeyVault.SaveIfAbsentResult(selectedKeyId, created = false)
                } else {
                    val name = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    vault.saveIfAbsent(name, secret)
                }
                preferences.edit { putString("last_key:$path", result.id) }
                if (result.created) {
                    val keys = vault.list()
                    _state.update { it.copy(keys = keys) }
                }
            }
        }.onFailure { error ->
            _state.update { it.copy(message = error.message ?: "密钥自动保存失败") }
        }
    }

    private fun refreshKeys() {
        viewModelScope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { vault.list() } }
                .onSuccess { keys -> _state.update { it.copy(keys = keys) } }
                .onFailure { error -> _state.update { it.copy(message = error.message ?: "读取密钥库失败") } }
        }
    }

    private fun appendLog(line: LogLine) {
        _state.update { state ->
            val lines = LogPolicy.appendBounded(state.run.lines, line, MAX_LOG_LINES)
            state.copy(run = state.run.copy(lines = lines))
        }
    }

    private fun rememberRun(path: String, code: Int) {
        preferences.edit {
            putLong("run_time:$path", System.currentTimeMillis())
            putInt("run_code:$path", code)
        }
        _state.update { state -> state.copy(files = mergeRunMetadata(state.files)) }
    }

    private fun mergeRunMetadata(files: List<SoFile>): List<SoFile> = files.map { file ->
        val time = preferences.getLong("run_time:${file.path}", -1L).takeIf { it >= 0 }
        val code = if (preferences.contains("run_code:${file.path}")) preferences.getInt("run_code:${file.path}", 0) else null
        file.copy(lastRunAt = time, lastExitCode = code)
    }

    private fun systemLine(text: String) = LogLine(System.currentTimeMillis(), LogLine.Stream.SYSTEM, text)

    private companion object {
        const val MAX_LOG_LINES = 500
        const val SCAN_DIRECTORY = "/data/adb"
    }
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
