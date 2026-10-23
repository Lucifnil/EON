package app.eon.launcher.data

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SecretEncodingTest {
    @Test fun `UTF8 roundtrip preserves whitespace and unicode`() {
        val secret = " 密钥🔑 ' ".toCharArray()
        val original = secret.copyOf()
        val bytes = SecretEncoding.encode(secret)
        try {
            assertArrayEquals(original, secret)
            assertArrayEquals(original, SecretEncoding.decode(bytes))
        } finally {
            secret.fill('\u0000')
            original.fill('\u0000')
            bytes.fill(0)
        }
    }
}
