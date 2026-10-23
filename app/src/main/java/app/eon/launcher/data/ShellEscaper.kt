package app.eon.launcher.data

object ShellEscaper {
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}

object ArgumentTokenizer {
    fun tokenize(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        input.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                    tokenStarted = true
                }
                char == '\\' && quote != '\'' -> escaping = true
                quote != null && char == quote -> {
                    quote = null
                    tokenStarted = true
                }
                quote != null -> current.append(char)
                char == '\'' || char == '"' -> {
                    quote = char
                    tokenStarted = true
                }
                char.isWhitespace() -> {
                    if (tokenStarted) {
                        result += current.toString()
                        current.clear()
                        tokenStarted = false
                    }
                }
                else -> {
                    current.append(char)
                    tokenStarted = true
                }
            }
        }
        require(!escaping) { "参数末尾存在未完成的转义符" }
        require(quote == null) { "参数中存在未闭合的引号" }
        if (tokenStarted) result += current.toString()
        return result
    }
}
