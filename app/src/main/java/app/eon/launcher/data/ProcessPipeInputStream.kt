package app.eon.launcher.data

import java.io.InputStream

/** Single-reader process output: wait interruptibly, and drain only the owned process's tail. */
internal class ProcessPipeInputStream(
    private val source: InputStream,
    private val isProcessAlive: () -> Boolean,
) : InputStream() {
    override fun read(): Int = if (awaitBytes() < 0) -1 else source.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        val available = awaitBytes()
        return if (available < 0) -1 else source.read(buffer, offset, minOf(length, available))
    }

    private fun awaitBytes(): Int {
        while (true) {
            // Also honor cancellation when output is continuous and no wait is needed.
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            // Check exit first: bytes written just before exit must still be drained.
            val exited = !isProcessAlive()
            val available = source.available()
            if (available > 0) return available
            if (exited) return -1
            // Never enter an uninterruptible pipe read waiting for an inherited writer.
            Thread.sleep(20L)
        }
    }

    override fun close() = source.close()
}
