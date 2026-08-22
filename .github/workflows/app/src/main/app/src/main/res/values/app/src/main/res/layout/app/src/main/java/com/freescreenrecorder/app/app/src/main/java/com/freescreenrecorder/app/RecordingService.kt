package com.freescreenrecorder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val ACTION_PAUSE = "PAUSE_RECORDING"
        const val ACTION_CANCEL = "CANCEL_RECORDING"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_FPS = "fps"
        const val EXTRA_AUDIO_MODE = "audio_mode"

        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var outputFile: File? = null
    private var isRecording = false
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                if (!isRecording) {
                    startRecording(intent)
                }
            }

            ACTION_PAUSE -> {
                pauseRecording()
            }

            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }

            ACTION_CANCEL -> {
                cancelRecording()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {

        val notification = createNotification("Preparing recording...")

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        val resultCode =
            intent.getIntExtra(EXTRA_RESULT_CODE, 0)

        val projectionData =
            intent.getParcelableExtra<Intent>(
                EXTRA_PROJECTION_DATA
            ) ?: return

        val resolution =
            intent.getStringExtra(EXTRA_RESOLUTION)
                ?: "1080p (1920×1080)"

        val fpsText =
            intent.getStringExtra(EXTRA_FPS)
                ?: "60 FPS"

        val width: Int
        val height: Int

        when {
            resolution.startsWith("4K") -> {
                width = 3840
                height = 2160
            }

            resolution.startsWith("2K") -> {
                width = 2560
                height = 1440
            }

            resolution.startsWith("720") -> {
                width = 1280
                height = 720
            }

            else -> {
                width = 1920
                height = 1080
            }
        }

        val fps =
            fpsText.substringBefore(" ")
                .toIntOrNull()
                ?: 60

        try {

            val projectionManager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    projectionData
                )

            if (mediaProjection == null) {
                stopSelf()
                return
            }

            val directory =
                getExternalFilesDir(
                    android.os.Environment.DIRECTORY_MOVIES
                )

            if (directory == null) {
                stopSelf()
                return
            }

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName =
                "ScreenRecording_" +
                    System.currentTimeMillis() +
                    ".mp4"

            outputFile =
                File(directory, fileName)

            mediaRecorder =
                if (Build.VERSION.SDK_INT >= 31) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            mediaRecorder?.apply {

                setVideoSource(
                    MediaRecorder.VideoSource.SURFACE
                )

                /*
                 * Microphone recording.
                 *
                 * Android does not allow a normal third-party
                 * application to capture every internal/system
                 * audio stream through MediaRecorder.
                 */
                setAudioSource(
                    MediaRecorder.AudioSource.MIC
                )

                setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
                )

                setVideoEncoder(
                    MediaRecorder.VideoEncoder.H264
                )

                setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
                )

                setVideoSize(
                    width,
                    height
                )

                setVideoFrameRate(
                    fps.coerceIn(24, 60)
                )

                setVideoEncodingBitRate(
                    calculateBitrate(
                        width,
                        height,
                        fps
                    )
                )

                setAudioEncodingBitRate(
                    128000
                )

                setAudioSamplingRate(
                    48000
                )

                setOutputFile(
                    outputFile!!.absolutePath
                )

                prepare()
            }

            val density =
                resources.displayMetrics.densityDpi

            virtualDisplay =
                mediaProjection!!.createVirtualDisplay(
                    "FreeScreenRecorder",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder!!.surface,
                    null,
                    null
                )

            mediaRecorder!!.start()

            isRecording = true
            isPaused = false

            updateNotification(
                "Recording ${width}×${height} @ ${fps} FPS"
            )

        } catch (e: Exception) {

            releaseRecorder()

            stopSelf()
        }
    }

    private fun calculateBitrate(
        width: Int,
        height: Int,
        fps: Int
    ): Int {

        val pixels = width * height

        return when {
            pixels >= 3840 * 2160 ->
                if (fps >= 60) 50000000 else 35000000

            pixels >= 2560 * 1440 ->
                if (fps >= 60) 30000000 else 22000000

            pixels >= 1920 * 1080 ->
                if (fps >= 60) 16000000 else 12000000

            else ->
                if (fps >= 60) 10000000 else 8000000
        }
    }

    private fun pauseRecording() {

        if (!isRecording) return

        if (Build.VERSION.SDK_INT >= 24) {

            try {

                if (!isPaused) {
                    mediaRecorder?.pause()
                    isPaused = true
                    updateNotification("Recording paused")
                } else {
                    mediaRecorder?.resume()
                    isPaused = false
                    updateNotification("Recording resumed")
                }

            } catch (_: Exception) {
            }
        }
    }

    private fun stopRecording() {

        if (!isRecording) return

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }

        isRecording = false
        isPaused = false

        releaseRecorder()

        updateNotification("Recording saved")

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun cancelRecording() {

        if (isRecording) {

            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
        }

        isRecording = false
        isPaused = false

        releaseRecorder()

        outputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }

        outputFile = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun releaseRecorder() {

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {
        }

        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }

        mediaRecorder = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen Recording",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Free Screen Recorder"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Free Screen Recorder"
            )
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    override fun onDestroy() {

        if (isRecording) {
            cancelRecording()
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
