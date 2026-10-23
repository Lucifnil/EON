package app.eon.launcher.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ProcessPipeInputStreamTest {
    @Test
    fun `exit is checked before inspecting the final bytes`() {
        var tail: ByteArrayInputStream? = null
        val source = object : InputStream() {
            override fun available() = tail?.available() ?: 0
            override fun read() = requireNotNull(tail).read()
        }
        val pipe = ProcessPipeInputStream(source) {
            if (tail == null) tail = ByteArrayInputStream("最后一行".toByteArray())
            false
        }
        assertEquals("最后一行", pipe.bufferedReader().readText())
    }

    @Test
    fun `UTF-8 characters survive one byte chunks`() {
        val text = "a密钥🔑\n尾行"
        val source = object : ByteArrayInputStream(text.toByteArray()) {
            override fun available() = minOf(1, super.available())
        }
        assertEquals(text, ProcessPipeInputStream(source) { false }.bufferedReader().readText())
    }

    @Test
    fun `continuous output still observes interruption`() {
        val pipe = ProcessPipeInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3))) { true }
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) { pipe.read() }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `empty process tail returns EOF and zero length reads remain valid`() {
        val pipe = ProcessPipeInputStream(ByteArrayInputStream(byteArrayOf())) { false }
        assertEquals(0, pipe.read(ByteArray(2), 1, 0))
        assertEquals(-1, pipe.read())
        assertThrows(IndexOutOfBoundsException::class.java) { pipe.read(ByteArray(2), 1, 2) }
    }

    @Test
    fun `read failures are not mistaken for EOF`() {
        val source = object : InputStream() {
            override fun available(): Int = throw IOException("fixture")
            override fun read(): Int = error("must not read")
        }
        assertThrows(IOException::class.java) { ProcessPipeInputStream(source) { false }.read() }
    }
}
