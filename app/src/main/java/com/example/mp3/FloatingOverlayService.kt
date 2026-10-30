package com.example.mp3

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * System Floating Overlay Service that renders a draggable floating bubble over other apps,
 * allowing the user to record internal app audio or microphone output from any running application.
 */
class FloatingOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_bubble_overlay_channel"
        const val NOTIFICATION_ID = 5050

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayRootView: FrameLayout? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateObserverJob: Job? = null

    // UI View elements
    private var pillContainer: LinearLayout? = null
    private var pillIndicatorDot: View? = null
    private var pillTimerText: TextView? = null
    private var pillQuickSaveBtn: ImageView? = null

    private var consoleCard: LinearLayout? = null
    private var consoleTimerText: TextView? = null
    private var consoleStatusText: TextView? = null
    private var consoleActionRecordBtn: Button? = null
    private var consoleActionSaveBtn: Button? = null
    private var consoleActionCancelBtn: Button? = null
    private var consoleSourceChip: TextView? = null

    override fun onCreate() {
        super.onCreate()
        AudioRecorderEngine.init(this)
        AudioRecorderEngine.setSystemOverlayActive(true)

        createNotification()
        setupFloatingWindow()
        observeRecorderState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        AudioRecorderEngine.setSystemOverlayActive(false)
        if (overlayRootView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayRootView)
            } catch (_: Exception) {}
            overlayRootView = null
        }
    }

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bubble Melayang di Atas Aplikasi",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Notifikasi saat Bubble Perekam aktif di atas aplikasi lain"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bubble Perekam Aktif di Layar")
            .setContentText("Ketuk bubble untuk merekam audio dari aplikasi yang sedang dibuka")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }
        windowLayoutParams = params

        val root = FrameLayout(this)
        overlayRootView = root

        buildViews(root)
        attachTouchListener(root, params)

        try {
            windowManager?.addView(root, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menampilkan overlay: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun buildViews(root: FrameLayout) {
        val dp = resources.displayMetrics.density

        // ==========================================
        // 1. MINIMIZED PILL VIEW
        // ==========================================
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E6181824"))
                cornerRadius = 24 * dp
                setStroke((1.5 * dp).toInt(), Color.parseColor("#7C4DFF"))
            }
            background = bg
            elevation = 12 * dp
        }
        pillContainer = pill

        // Glowing dot
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt()).apply {
                rightMargin = (8 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
        }
        pillIndicatorDot = dot
        pill.addView(dot)

        // Timer text
        val timer = TextView(this).apply {
            text = "🎙️ Rekam"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }
        pillTimerText = timer
        pill.addView(timer)

        // Quick Save icon (only shown when recording)
        val saveIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setColorFilter(Color.parseColor("#00E676"))
            layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                leftMargin = (8 * dp).toInt()
            }
            visibility = View.GONE
            setOnClickListener {
                AudioRecorderEngine.stopAndSaveRecording(autoAddCover = true)
            }
        }
        pillQuickSaveBtn = saveIcon
        pill.addView(saveIcon)

        root.addView(pill)

        // ==========================================
        // 2. EXPANDED CONSOLE CARD VIEW
        // ==========================================
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams((290 * dp).toInt(), FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F512121E"))
                cornerRadius = 20 * dp
                setStroke((1.5 * dp).toInt(), Color.parseColor("#7C4DFF"))
            }
            background = bg
            elevation = 16 * dp
            visibility = View.GONE
        }
        consoleCard = card

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cardTitle = TextView(this).apply {
            text = "Bubble Audio Sistem"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        titleCol.addView(cardTitle)

        val cardSub = TextView(this).apply {
            text = "Merekam Audio Aplikasi Lain (HD 48kHz)"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
        }
        consoleStatusText = cardSub
        titleCol.addView(cardSub)
        headerRow.addView(titleCol)

        // Minimize Button
        val minBtn = TextView(this).apply {
            text = "➖"
            textSize = 14f
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            setOnClickListener { setExpandedState(false) }
        }
        headerRow.addView(minBtn)

        // Close Overlay Button
        val closeBtn = TextView(this).apply {
            text = "✖"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 14f
            setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            setOnClickListener {
                if (AudioRecorderEngine.recorderState.value.isRecording) {
                    AudioRecorderEngine.cancelRecording()
                }
                stopSelf()
            }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Source Chip Row
        val sourceChip = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            }
            text = "🎧 Sumber: Audio Aplikasi (Internal)"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3300E5FF"))
                cornerRadius = 8 * dp
            }
            setOnClickListener {
                if (!AudioRecorderEngine.recorderState.value.isRecording) {
                    val current = AudioRecorderEngine.recorderState.value.source
                    val next = if (current == AudioRecordSource.INTERNAL_APP) AudioRecordSource.MICROPHONE else AudioRecordSource.INTERNAL_APP
                    AudioRecorderEngine.setSource(next)
                }
            }
        }
        consoleSourceChip = sourceChip
        card.addView(sourceChip)

        // Big Digital Timer
        val bigTimer = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        consoleTimerText = bigTimer
        card.addView(bigTimer)

        // Main Action Buttons Row: Rekam/Jeda, Simpan, Batal
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val recordBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1.2f).apply {
                rightMargin = (4 * dp).toInt()
            }
            text = "⏺️ Rekam"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C4DFF"))
                cornerRadius = 10 * dp
            }
            setOnClickListener {
                val state = AudioRecorderEngine.recorderState.value
                if (!state.isRecording) {
                    if (state.source == AudioRecordSource.INTERNAL_APP) {
                        CapturePermissionActivity.launch(this@FloatingOverlayService)
                    } else {
                        AudioRecorderEngine.startMicRecording()
                    }
                } else {
                    if (state.isPaused) {
                        AudioRecorderEngine.resumeRecording()
                    } else {
                        AudioRecorderEngine.pauseRecording()
                    }
                }
            }
        }
        consoleActionRecordBtn = recordBtn
        actionRow.addView(recordBtn)

        val saveBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply {
                rightMargin = (4 * dp).toInt()
            }
            text = "💾 Simpan"
            setTextColor(Color.BLACK)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00E676"))
                cornerRadius = 10 * dp
            }
            setOnClickListener {
                AudioRecorderEngine.stopAndSaveRecording(autoAddCover = true) {
                    setExpandedState(false)
                }
            }
        }
        consoleActionSaveBtn = saveBtn
        actionRow.addView(saveBtn)

        val cancelBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 0.9f)
            text = "❌ Batal"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 11f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FF5252"))
                cornerRadius = 10 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#FF5252"))
            }
            setOnClickListener {
                AudioRecorderEngine.cancelRecording()
                setExpandedState(false)
            }
        }
        consoleActionCancelBtn = cancelBtn
        actionRow.addView(cancelBtn)
        card.addView(actionRow)

        // Open App Button
        val openAppBtn = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
            }
            text = "📲 Buka CoverCraft MP3"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            setOnClickListener {
                val appIntent = Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(appIntent)
            }
        }
        card.addView(openAppBtn)

        root.addView(card)
    }

    private fun setExpandedState(expanded: Boolean) {
        isExpanded = expanded
        if (expanded) {
            pillContainer?.visibility = View.GONE
            consoleCard?.visibility = View.VISIBLE
        } else {
            consoleCard?.visibility = View.GONE
            pillContainer?.visibility = View.VISIBLE
        }
    }

    private fun attachTouchListener(root: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasMoved = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        hasMoved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // It was a click! Toggle expanded state
                        setExpandedState(!isExpanded)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun observeRecorderState() {
        stateObserverJob = scope.launch {
            AudioRecorderEngine.recorderState.collectLatest { state ->
                val minutes = state.elapsedSeconds / 60
                val seconds = state.elapsedSeconds % 60
                val timeFormatted = "%02d:%02d".format(minutes, seconds)

                // Update Minimized Pill
                if (state.isRecording) {
                    pillTimerText?.text = "🔴 $timeFormatted"
                    pillIndicatorDot?.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#FF5252"))
                    }
                    pillQuickSaveBtn?.visibility = View.VISIBLE
                } else {
                    pillTimerText?.text = if (state.source == AudioRecordSource.INTERNAL_APP) "🎧 Audio App" else "🎙️ Mic HD"
                    pillIndicatorDot?.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#00E5FF"))
                    }
                    pillQuickSaveBtn?.visibility = View.GONE
                }

                // Update Expanded Console
                consoleTimerText?.text = timeFormatted
                consoleTimerText?.setTextColor(if (state.isRecording) Color.parseColor("#FF5252") else Color.WHITE)

                consoleSourceChip?.text = if (state.source == AudioRecordSource.INTERNAL_APP) {
                    "🎧 Sumber: Audio Aplikasi (Internal)"
                } else {
                    "🎙️ Sumber: Mikrofon HD (48kHz)"
                }

                if (state.isRecording) {
                    consoleStatusText?.text = if (state.isPaused) "⏸️ Perekaman Dijeda" else "🔴 Sedang Merekam..."
                    consoleActionRecordBtn?.text = if (state.isPaused) "▶️ Lanjut" else "⏸️ Jeda"
                    consoleActionSaveBtn?.visibility = View.VISIBLE
                    consoleActionCancelBtn?.visibility = View.VISIBLE
                } else {
                    consoleStatusText?.text = "Kualitas Jernih 48kHz HD"
                    consoleActionRecordBtn?.text = "⏺️ Rekam"
                    consoleActionSaveBtn?.visibility = View.GONE
                    consoleActionCancelBtn?.visibility = View.GONE
                }
            }
        }
    }
}
