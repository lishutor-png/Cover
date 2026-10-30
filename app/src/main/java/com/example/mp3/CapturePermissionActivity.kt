package com.example.mp3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Lightweight translucent activity to request MediaProjection permission
 * when initiated from background/system floating overlay.
 */
class CapturePermissionActivity : Activity() {

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                AudioRecorderEngine.startInternalAudioRecording(resultCode, data)
            } else {
                AudioRecorderEngine.onInternalAudioPermissionFailed("Izin rekam audio sistem dibatalkan pengguna")
            }
        }
        finish()
    }
}
