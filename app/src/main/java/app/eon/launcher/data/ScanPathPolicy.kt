package app.eon.launcher.data

/** Shared path rules for configuration, scanning and launch validation. */
object ScanPathPolicy {
    const val DEFAULT_PATH = "/data/adb"

    fun normalize(path: String): String {
        require(path.startsWith('/') && '\u0000' !in path) { "请选择有效的绝对目录" }
        val parts = mutableListOf<String>()
        path.split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(it)
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun initialPaths(saved: Set<String>?): List<String> =
        (saved ?: setOf(DEFAULT_PATH)).map(::normalize).distinct().sorted()

    fun allowsFile(paths: List<String>, path: String): Boolean {
        val normalized = runCatching { normalize(path) }.getOrNull() ?: return false
        return normalized.endsWith(".so") && parent(normalized) in paths
    }

    fun parent(path: String): String = normalize(path).substringBeforeLast('/', "").ifEmpty { "/" }
}
