package app.eon.launcher.data

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/** UTF-8 conversion without creating immutable plaintext Strings in the vault. */
internal object SecretEncoding {
    fun encode(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        return try {
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } finally {
            if (buffer.hasArray()) buffer.array().fill(0)
        }
    }

    fun decode(bytes: ByteArray): CharArray {
        val buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        return try {
            CharArray(buffer.remaining()).also { buffer.get(it) }
        } finally {
            if (buffer.hasArray()) buffer.array().fill('\u0000')
        }
    }
}
