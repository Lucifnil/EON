package app.eon.launcher.model

enum class RootState { CHECKING, READY, DENIED, UNAVAILABLE }

data class SoFile(
    val path: String,
    val executable: Boolean,
    val lastRunAt: Long? = null,
    val lastExitCode: Int? = null,
) {
    val name: String get() = path.substringAfterLast('/')
    val directory: String get() = path.substringBeforeLast('/', "/")
}

data class KeySummary(val id: String, val name: String)

data class DirectoryListing(val path: String, val children: List<String>)

enum class ProcessStatus { IDLE, STARTING, RUNNING, STOPPING, FINISHED, FAILED }

data class LogLine(
    val timestamp: Long,
    val stream: Stream,
    val text: String,
) {
    enum class Stream { SYSTEM, STDOUT, STDERR }
}

data class RunState(
    val status: ProcessStatus = ProcessStatus.IDLE,
    val fileName: String? = null,
    val startedAt: Long? = null,
    val exitCode: Int? = null,
    val lines: List<LogLine> = emptyList(),
)

data class EonUiState(
    val rootState: RootState = RootState.CHECKING,
    val scanPaths: List<String> = emptyList(),
    val scanErrors: Map<String, String> = emptyMap(),
    val showScanPathTip: Boolean = false,
    val isScanning: Boolean = false,
    val files: List<SoFile> = emptyList(),
    val query: String = "",
    val keys: List<KeySummary> = emptyList(),
    val run: RunState = RunState(),
    val message: String? = null,
)
