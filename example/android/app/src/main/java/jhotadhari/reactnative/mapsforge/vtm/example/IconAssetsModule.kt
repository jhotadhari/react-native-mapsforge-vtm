package jhotadhari.reactnative.mapsforge.vtm.example

import android.content.Context
import android.content.SharedPreferences
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.io.FileOutputStream

/**
 * Copies icon/font assets from the APK's assets/ directory to the app's
 * internal files directory at first launch, then exposes the resulting
 * base path as a constant so JS can build [filePath] / [fontPath] strings
 * for marker symbols.
 *
 * Asset files are read from:
 *   assets/icons/    -> context.filesDir/icons/
 *   assets/fonts/    -> context.filesDir/fonts/
 *
 * Copying runs once (idempotent, gated by SharedPreferences).
 */
class IconAssetsModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "IconAssetsModule"
        private const val PREFS_NAME = "icon_assets_prefs"
        private const val KEY_COPIED = "assets_copied"
    }

    override fun getName(): String = NAME

    override fun getConstants(): MutableMap<String, Any> {
        val basePath = reactApplicationContext.filesDir.absolutePath
        return mutableMapOf("basePath" to basePath)
    }

    /**
     * Called from JS to ensure assets are copied. Safe to call multiple
     * times — the copy only happens once per app install.
     */
    @ReactMethod
    fun ensureAssetsCopied() {
        val prefs: SharedPreferences =
            reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_COPIED, false)) return

        val filesDir = reactApplicationContext.filesDir
        val assetManager = reactApplicationContext.assets

        // Copy icons/
        copyAssetDir(assetManager, filesDir, "icons")
        // Copy fonts/
        copyAssetDir(assetManager, filesDir, "fonts")

        prefs.edit().putBoolean(KEY_COPIED, true).apply()
    }

    private fun copyAssetDir(
        assetManager: android.content.res.AssetManager,
        filesDir: File,
        dirName: String
    ) {
        try {
            val files = assetManager.list(dirName) ?: return
            val targetDir = File(filesDir, dirName)
            targetDir.mkdirs()

            for (fileName in files) {
                val assetPath = "$dirName/$fileName"
                val targetFile = File(targetDir, fileName)

                // Skip if already present (e.g., app update kept filesDir)
                if (targetFile.exists()) continue

                try {
                    assetManager.open(assetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    // Individual file copy failure is non-fatal; the marker
                    // will fall back to a red circle on the native side.
                    android.util.Log.w(
                        NAME,
                        "Failed to copy asset $assetPath: ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(
                NAME,
                "Failed to list asset dir $dirName: ${e.message}"
            )
        }
    }
}
