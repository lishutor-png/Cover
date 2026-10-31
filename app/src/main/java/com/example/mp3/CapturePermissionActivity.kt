package com.example.mp3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/**
 * Lightweight translucent activity to request MediaProjection permission
 * when opening the bubble or initiated from background system floating overlay.
 */
class CapturePermissionActivity : Activity() {

    companion object {
        const val EXTRA_AUTO_START = "extra_auto_start"

        fun launch(context: Context, autoStart: Boolean = false) {
            val intent = Intent(context, CapturePermissionActivity::class.java).apply {
                putExtra(EXTRA_AUTO_START, autoStart)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }

        fun launchForPreAuth(context: Context) {
            launch(context, autoStart = false)
        }
    }

    private var autoStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStart = intent?.getBooleanExtra(EXTRA_AUTO_START, false) ?: false

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(projectionManager.createScreenCaptureIntent(), 9090)
            } catch (e: Exception) {
                AudioRecorderEngine.onInternalAudioPermissionFailed(e.localizedMessage ?: "Gagal meminta izin rekam audio")
                finish()
            }
        } else {
            AudioRecorderEngine.onInternalAudioPermissionFailed("MediaProjection tidak tersedia")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9090) {
            if (resultCode == RESULT_OK && data != null) {
                // Store the pre-authorized token so all future recordings start instantly!
                AudioRecorderEngine.setPreAuthorizedToken(resultCode, data)
                if (autoStart) {
                    AudioRecorderEngine.startInternalAudioRecording(resultCode, data)
                } else {
                    Toast.makeText(
                        this,
                        "✅ Izin transmisi siap! Buka YouTube/Spotify & tekan Rekam di bubble kapan saja.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                AudioRecorderEngine.onInternalAudioPermissionFailed("Izin transmisi audio sistem dibatalkan")
            }
        }
        finish()
    }
}
