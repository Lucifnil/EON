package app.eon.launcher.data

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AesGcmCodec {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext)
        return cipher.iv + encrypted
    }

    @Throws(AEADBadTagException::class)
    fun decrypt(packed: ByteArray, key: SecretKey): ByteArray {
        require(packed.size > IV_SIZE) { "加密数据损坏" }
        val iv = packed.copyOfRange(0, IV_SIZE)
        val encrypted = packed.copyOfRange(IV_SIZE, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }
}
