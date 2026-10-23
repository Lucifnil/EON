package app.eon.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

class ScanPathRepository(context: Context) {
    private val preferences = context.getSharedPreferences("eon_scan_paths", Context.MODE_PRIVATE)

    @Synchronized
    fun paths(): List<String> = ScanPathPolicy.initialPaths(preferences.getStringSet("paths", null))

    @Synchronized
    fun add(path: String): Boolean {
        val normalized = ScanPathPolicy.normalize(path)
        val current = paths()
        if (normalized in current) return false
        write(current + normalized)
        return true
    }

    @Synchronized
    fun remove(path: String) = write(paths().filterNot { it == path })

    fun shouldShowTip(): Boolean = !preferences.getBoolean("tip_seen", false)

    fun markTipSeen() = preferences.edit { putBoolean("tip_seen", true) }

    // Preserve the result of commit so a disk write failure is visible to the user.
    @SuppressLint("UseKtx")
    private fun write(paths: List<String>) {
        check(preferences.edit().putStringSet("paths", paths.toSet()).commit()) { "扫描路径保存失败" }
    }
}
