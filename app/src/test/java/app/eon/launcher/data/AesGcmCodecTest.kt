package app.eon.launcher.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCodecTest {
    @Test
    fun `round trip restores plaintext`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "仅用于测试的密钥".toByteArray()

        val packed = AesGcmCodec.encrypt(plaintext, key)

        assertArrayEquals(plaintext, AesGcmCodec.decrypt(packed, key))
    }

    @Test
    fun `each encryption uses a new iv`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "same secret".toByteArray()

        val first = AesGcmCodec.encrypt(plaintext, key)
        val second = AesGcmCodec.encrypt(plaintext, key)

        assertFalse(first.contentEquals(second))
    }
}
