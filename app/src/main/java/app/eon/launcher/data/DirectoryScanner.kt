package app.eon.launcher.data

import app.eon.launcher.model.SoFile
import kotlinx.coroutines.CancellationException

data class ScanResult(val files: List<SoFile>, val errors: Map<String, String>)

/** Per-directory failures never discard results from the remaining directories. */
class DirectoryScanner(private val runner: RootRunner) {
    suspend fun scan(paths: List<String>): ScanResult {
        val visited = mutableSetOf<String>()
        val files = mutableListOf<SoFile>()
        val errors = linkedMapOf<String, String>()
        paths.forEach { path ->
            try {
                val canonical = runner.resolveDirectory(path)
                if (canonical !in visited) {
                    files += runner.scan(path)
                    visited += canonical
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errors[path] = error.message ?: "无法访问此目录"
            }
        }
        return ScanResult(files.distinctBy { it.path }.sortedWith(compareBy({ it.name.lowercase() }, { it.path })), errors)
    }
}
