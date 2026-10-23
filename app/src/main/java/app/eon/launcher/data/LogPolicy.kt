package app.eon.launcher.data

object LogPolicy {
    const val MAX_LINE_LENGTH = 4096

    fun redact(text: String, secret: CharArray): String {
        if (secret.isEmpty()) return text
        val sensitive = String(secret)
        return if (sensitive.isNotEmpty()) text.replace(sensitive, "••••") else text
    }

    fun <T> appendBounded(existing: List<T>, item: T, capacity: Int): List<T> {
        require(capacity > 0) { "日志容量必须大于 0" }
        return (existing + item).takeLast(capacity)
    }

    // Keep enough lookahead to redact a secret crossing the visible truncation boundary.
    fun readLines(reader: java.io.Reader, secret: CharArray, onLine: (String) -> Unit) {
        val line = StringBuilder()
        val limit = MAX_LINE_LENGTH + secret.size
        var truncated = false
        var skipLf = false
        fun emit() {
            val redacted = redact(line.toString(), secret)
            onLine(redacted.take(MAX_LINE_LENGTH) + if (truncated || redacted.length > MAX_LINE_LENGTH) "…" else "")
            line.setLength(0)
            truncated = false
        }
        val buffer = CharArray(2048)
        try {
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                for (index in 0 until count) {
                    val char = buffer[index]
                    if (char == '\n' && skipLf) { skipLf = false; continue }
                    skipLf = char == '\r'
                    if (char == '\r' || char == '\n') emit()
                    else if (line.length < limit) line.append(char)
                    else truncated = true
                }
            }
            if (line.isNotEmpty() || truncated) emit()
        } finally {
            buffer.fill('\u0000')
            line.setLength(0)
        }
    }
}
