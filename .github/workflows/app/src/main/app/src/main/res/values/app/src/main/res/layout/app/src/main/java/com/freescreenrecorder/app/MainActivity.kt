package com.freescreenrecorder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val projectionRequestCode = 1001
    private val microphoneRequestCode = 1002

    private lateinit var resolutionSpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var pauseButton: Button
    private lateinit var cancelButton: Button
    private lateinit var audioButton: Button

    private var audioMode = "Internal + Microphone"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        resolutionSpinner = findViewById(R.id.resolutionSpinner)
        fpsSpinner = findViewById(R.id.fpsSpinner)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        pauseButton = findViewById(R.id.pauseButton)
        cancelButton = findViewById(R.id.cancelButton)
        audioButton = findViewById(R.id.audioButton)

        setupSpinners()

        audioButton.setOnClickListener {
            audioMode = when (audioMode) {
                "Internal + Microphone" -> "Microphone Only"
                "Microphone Only" -> "Internal Audio"
                else -> "Internal + Microphone"
            }

            audioButton.text = "Audio: $audioMode"
        }

        startButton.setOnClickListener {
            requestRecording()
        }

        stopButton.setOnClickListener {
            sendServiceCommand(RecordingService.ACTION_STOP)
        }

        pauseButton.setOnClickListener {
            sendServiceCommand(RecordingService.ACTION_PAUSE)
        }

        cancelButton.setOnClickListener {
            sendServiceCommand(RecordingService.ACTION_CANCEL)
        }
    }

    private fun setupSpinners() {

        val resolutions = arrayOf(
            "4K (3840×2160)",
            "2K (2560×1440)",
            "1080p (1920×1080)",
            "720p (1280×720)"
        )

        val fps = arrayOf(
            "60 FPS",
            "50 FPS",
            "30 FPS",
            "24 FPS"
        )

        resolutionSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resolutions
        )

        fpsSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            fps
        )
    }

    private fun requestRecording() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                microphoneRequestCode
            )
            return
        }

        requestScreenCapture()
    }

    private fun requestScreenCapture() {

        val intent = projectionManager.createScreenCaptureIntent()

        startActivityForResult(
            intent,
            projectionRequestCode
        )
    }

    @Deprecated("Use Activity Result API in a future update")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != projectionRequestCode) return

        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        val serviceIntent = Intent(
            this,
            RecordingService::class.java
        ).apply {

            action = RecordingService.ACTION_START

            putExtra(
                RecordingService.EXTRA_RESULT_CODE,
                resultCode
            )

            putExtra(
                RecordingService.EXTRA_PROJECTION_DATA,
                data
            )

            putExtra(
                RecordingService.EXTRA_RESOLUTION,
                resolutionSpinner.selectedItem.toString()
            )

            putExtra(
                RecordingService.EXTRA_FPS,
                fpsSpinner.selectedItem.toString()
            )

            putExtra(
                RecordingService.EXTRA_AUDIO_MODE,
                audioMode
            )
        }

        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )

        startButton.isEnabled = false
        stopButton.isEnabled = true
        pauseButton.isEnabled = true
        cancelButton.isEnabled = true
    }

    private fun sendServiceCommand(action: String) {

        val intent = Intent(this, RecordingService::class.java)
        intent.action = action

        startService(intent)

        if (action == RecordingService.ACTION_STOP ||
            action == RecordingService.ACTION_CANCEL
        ) {
            startButton.isEnabled = true
            stopButton.isEnabled = false
            pauseButton.isEnabled = false
            cancelButton.isEnabled = false
        }
    }
}
