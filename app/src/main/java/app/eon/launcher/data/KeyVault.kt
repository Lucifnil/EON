package app.eon.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.eon.launcher.model.KeySummary
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyVault(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class SaveIfAbsentResult(
        val id: String,
        val created: Boolean,
    )

    @Synchronized
    fun list(): List<KeySummary> {
        val array = JSONArray(preferences.getString(ITEMS, "[]"))
        return buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(KeySummary(item.getString("id"), item.getString("name")))
            }
        }
    }

    @Synchronized
    fun read(id: String): CharArray? {
        val array = JSONArray(preferences.getString(ITEMS, "[]"))
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            if (item.getString("id") == id) return decrypt(item.getString("value"))
        }
        return null
    }

    @Synchronized
    fun contains(id: String): Boolean {
        val array = JSONArray(preferences.getString(ITEMS, "[]"))
        repeat(array.length()) { index ->
            if (array.getJSONObject(index).getString("id") == id) return true
        }
        return false
    }

    @Synchronized
    fun save(id: String?, name: String, secret: CharArray): String {
        require(name.isNotBlank()) { "密钥名称不能为空" }
        require(secret.isNotEmpty()) { "密钥不能为空" }
        val actualId = id ?: UUID.randomUUID().toString()
        val old = JSONArray(preferences.getString(ITEMS, "[]"))
        val next = JSONArray()
        repeat(old.length()) { index ->
            val item = old.getJSONObject(index)
            if (item.getString("id") != actualId) next.put(item)
        }
        next.put(JSONObject().apply {
            put("id", actualId)
            put("name", name.trim())
            put("value", encrypt(secret))
        })
        writeItems(next)
        return actualId
    }

    @Synchronized
    fun saveIfAbsent(name: String, secret: CharArray): SaveIfAbsentResult {
        require(name.isNotBlank()) { "密钥名称不能为空" }
        require(secret.isNotEmpty()) { "密钥不能为空" }
        val old = JSONArray(preferences.getString(ITEMS, "[]"))
        val names = mutableSetOf<String>()
        repeat(old.length()) { index ->
            val item = old.getJSONObject(index)
            names += item.getString("name")
            val existing = decrypt(item.getString("value"))
            try {
                if (existing.contentEquals(secret)) {
                    return SaveIfAbsentResult(item.getString("id"), created = false)
                }
            } finally {
                existing.fill('\u0000')
            }
        }

        val uniqueName = generateSequence(1) { it + 1 }
            .map { suffix -> if (suffix == 1) name else "${name}_$suffix" }
            .first { it !in names }
        val id = UUID.randomUUID().toString()
        old.put(JSONObject().apply {
            put("id", id)
            put("name", uniqueName)
            put("value", encrypt(secret))
        })
        writeItems(old)
        return SaveIfAbsentResult(id, created = true)
    }

    @Synchronized
    fun delete(id: String) {
        val old = JSONArray(preferences.getString(ITEMS, "[]"))
        val next = JSONArray()
        repeat(old.length()) { index ->
            val item = old.getJSONObject(index)
            if (item.getString("id") != id) next.put(item)
        }
        writeItems(next)
    }

    // KTX edit returns Unit; retain commit's Boolean so failed persistence is reported.
    @SuppressLint("UseKtx")
    private fun writeItems(items: JSONArray) {
        check(preferences.edit().putString(ITEMS, items.toString()).commit()) { "密钥库写入失败" }
    }

    private fun getOrCreateKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "密钥库加密凭据已丢失" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(secret: CharArray): String {
        val bytes = SecretEncoding.encode(secret)
        try {
            val packed = AesGcmCodec.encrypt(bytes, getOrCreateKey(create = true))
            return Base64.encodeToString(packed, Base64.NO_WRAP)
        } finally {
            bytes.fill(0)
        }
    }

    private fun decrypt(value: String): CharArray {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        return try {
            val decrypted = AesGcmCodec.decrypt(packed, getOrCreateKey(create = false))
            try {
                SecretEncoding.decode(decrypted)
            } finally {
                decrypted.fill(0)
            }
        } finally {
            packed.fill(0)
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "eon_vault_aes_key_v1"
        const val PREFS = "eon_encrypted_vault"
        const val ITEMS = "items"
    }
}
