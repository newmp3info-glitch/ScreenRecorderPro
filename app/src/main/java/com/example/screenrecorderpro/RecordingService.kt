package com.example.screenrecorderpro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    companion object {

        const val START = "START"
        const val STOP = "STOP"

        const val RESULT_CODE = "result"
        const val PROJECTION_DATA = "data"
        const val RESOLUTION = "resolution"
        const val FPS = "fps"
        const val MIC = "mic"
        const val OVERLAY = "overlay"

        const val CHANNEL = "recording"

        private const val NOTIFICATION_ID = 1001
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null

    private var outputFile: File? = null
    private var outputUri: Uri? = null

    private var overlayRunning = false
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Screen recording",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            START -> {

                val useMic =
                    intent.getBooleanExtra(MIC, false)

                val notification =
                    Notification.Builder(
                        this,
                        CHANNEL
                    )
                        .setContentTitle(
                            "Screen Recorder Pro"
                        )
                        .setContentText(
                            "Recording in progress"
                        )
                        .setSmallIcon(
                            android.R.drawable.ic_media_play
                        )
                        .setOngoing(true)
                        .build()

                if (Build.VERSION.SDK_INT >= 29) {

                    var type =
                        ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

                    if (useMic) {
                        type =
                            type or
                                ServiceInfo
                                    .FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }

                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        type
                    )

                } else {

                    startForeground(
                        NOTIFICATION_ID,
                        notification
                    )
                }

                try {

                    startCapture(
                        intent,
                        useMic
                    )

                } catch (e: Exception) {

                    e.printStackTrace()

                    stopCapture(
                        "Recording failed: ${e.message}"
                    )
                }
            }

            STOP -> {

                stopCapture(
                    "Recording saved"
                )
            }
        }

        return START_NOT_STICKY
    }

    private fun startCapture(
        intent: Intent,
        useMic: Boolean
    ) {

        if (isRecording) {
            return
        }

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as android.view.WindowManager

        val metrics =
            android.util.DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(
            metrics
        )

        val resolution =
            intent.getStringExtra(
                RESOLUTION
            ) ?: "Device default"

        val recordingSize =
            calculateSize(
                metrics,
                resolution
            )

        val projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val projectionData =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    PROJECTION_DATA,
                    Intent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(
                    PROJECTION_DATA
                )
            }
                ?: throw IllegalStateException(
                    "Screen projection data is missing"
                )

        projection =
            projectionManager.getMediaProjection(
                intent.getIntExtra(
                    RESULT_CODE,
                    0
                ),
                projectionData
            )

        val fileName =
            "REC_" +
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date()) +
                ".mp4"

        /*
         * Android 10+:
         * Save directly into public Movies/ScreenRecorderPro
         * through MediaStore.
         *
         * Older Android:
         * Save into public Movies/ScreenRecorderPro.
         */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Video.Media.DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Video.Media.MIME_TYPE,
                        "video/mp4"
                    )

                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES +
                            "/ScreenRecorderPro"
                    )

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
                }

            outputUri =
                contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                )
                    ?: throw IllegalStateException(
                        "Could not create video file"
                    )

        } else {

            val moviesDirectory =
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                )

            val folder =
                File(
                    moviesDirectory,
                    "ScreenRecorderPro"
                )

            if (!folder.exists()) {
                folder.mkdirs()
            }

            outputFile =
                File(
                    folder,
                    fileName
                )
        }

        recorder =
            MediaRecorder().apply {

                if (useMic) {
                    setAudioSource(
                        MediaRecorder.AudioSource.MIC
                    )
                }

                setVideoSource(
                    MediaRecorder.VideoSource.SURFACE
                )

                setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    setOutputFile(
                        contentResolver.openFileDescriptor(
                            outputUri!!,
                            "w"
                        )!!.fileDescriptor
                    )

                } else {

                    setOutputFile(
                        outputFile!!.absolutePath
                    )
                }

                setVideoEncoder(
                    MediaRecorder.VideoEncoder.H264
                )

                if (useMic) {

                    setAudioEncoder(
                        MediaRecorder.AudioEncoder.AAC
                    )
                }

                setVideoSize(
                    recordingSize.first,
                    recordingSize.second
                )

                val fps =
                    intent.getIntExtra(
                        FPS,
                        30
                    ).coerceIn(
                        15,
                        60
                    )

                setVideoFrameRate(fps)

                val bitrate =
                    when {

                        recordingSize.first >= 3840 ->
                            35_000_000

                        recordingSize.first >= 2560 ->
                            20_000_000

                        else ->
                            12_000_000
                    }

                setVideoEncodingBitRate(
                    bitrate
                )

                prepare()
            }

        display =
            projection?.createVirtualDisplay(
                "ScreenRecorderPro",
                recordingSize.first,
                recordingSize.second,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface,
                null,
                null
            )

        recorder!!.start()

        isRecording = true

        if (
            intent.getBooleanExtra(
                OVERLAY,
                false
            )
        ) {

            startService(
                Intent(
                    this,
                    OverlayService::class.java
                )
            )

            overlayRunning = true
        }
    }

    private fun calculateSize(
        metrics: android.util.DisplayMetrics,
        choice: String
    ): Pair<Int, Int> {

        val screenWidth =
            metrics.widthPixels

        val screenHeight =
            metrics.heightPixels

        val targetHeight =
            when (choice) {

                "1080p" ->
                    1080

                "1440p" ->
                    1440

                "2160p (4K)" ->
                    2160

                else ->
                    screenHeight
            }.coerceAtMost(
                screenHeight
            )

        val ratio =
            screenWidth.toFloat() /
                screenHeight.toFloat()

        var width =
            (targetHeight * ratio)
                .toInt()

        var height =
            targetHeight

        width -= width % 2
        height -= height % 2

        return if (
            screenWidth >= screenHeight
        ) {

            width to height

        } else {

            height to width
        }
    }

    private fun stopCapture(
        message: String
    ) {

        if (!isRecording) {

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

            stopSelf()

            return
        }

        var successfullySaved = false

        try {

            recorder?.stop()

            successfullySaved = true

        } catch (e: Exception) {

            e.printStackTrace()

            outputFile?.delete()

            if (outputUri != null) {

                try {

                    contentResolver.delete(
                        outputUri!!,
                        null,
                        null
                    )

                } catch (_: Exception) {
                }
            }

        } finally {

            try {
                recorder?.reset()
            } catch (_: Exception) {
            }

            try {
                recorder?.release()
            } catch (_: Exception) {
            }

            recorder = null

            try {
                display?.release()
            } catch (_: Exception) {
            }

            display = null

            try {
                projection?.stop()
            } catch (_: Exception) {
            }

            projection = null

            if (overlayRunning) {

                stopService(
                    Intent(
                        this,
                        OverlayService::class.java
                    )
                )

                overlayRunning = false
            }

            isRecording = false
        }

        /*
         * Android 10+:
         * IS_PENDING = 0 makes the video visible
         * in Gallery/Files/Movies.
         */

        if (
            successfullySaved &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            outputUri != null
        ) {

            try {

                val values =
                    ContentValues().apply {

                        put(
                            MediaStore.Video.Media.IS_PENDING,
                            0
                        )
                    }

                contentResolver.update(
                    outputUri!!,
                    values,
                    null,
                    null
                )

            } catch (e: Exception) {

                e.printStackTrace()
                successfullySaved = false
            }
        }

        if (successfullySaved) {

            showNotification(
                "Recording saved",
                "Saved to Movies/ScreenRecorderPro"
            )

        } else {

            showNotification(
                "Recording failed",
                "The video could not be saved."
            )
        }

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun showNotification(
        title: String,
        text: String
    ) {

        val notification =
            Notification.Builder(
                this,
                CHANNEL
            )
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setAutoCancel(true)
                .build()

        getSystemService(
            NotificationManager::class.java
        ).notify(
            NOTIFICATION_ID + 1,
            notification
        )
    }

    override fun onDestroy() {

        if (isRecording) {
            stopCapture("Service stopped")
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
