package com.example.screenrecorderpro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var mic: CheckBox
    private lateinit var overlay: CheckBox
    private val projectionRequest = 9001
    private val permissionRequest = 9002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status); start = findViewById(R.id.start); stop = findViewById(R.id.stop)
        mic = findViewById(R.id.mic); overlay = findViewById(R.id.overlay)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        findViewById<Spinner>(R.id.resolution).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Device default", "1080p", "1440p", "2160p (4K)"))
        findViewById<Spinner>(R.id.fps).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("30 FPS", "60 FPS"))
        start.setOnClickListener { begin() }
        stop.setOnClickListener { stopRecording() }
    }

    private fun begin() {
        if (mic.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequest); return
        }
        if (overlay.isChecked && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "Allow Display over other apps, then press Start again."; return
        }
        status.text = "Requesting screen permission..."
        startActivityForResult(projectionManager.createScreenCaptureIntent(), projectionRequest)
    }

    @Deprecated("Activity result API kept compact for this project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == Activity.RESULT_OK && data != null) {
            val resolution = findViewById<Spinner>(R.id.resolution).selectedItem.toString()
            val fps = findViewById<Spinner>(R.id.fps).selectedItem.toString().substringBefore(" ").toInt()
            val i = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.START
                putExtra(RecordingService.RESULT_CODE, resultCode)
                putExtra(RecordingService.PROJECTION_DATA, data)
                putExtra(RecordingService.RESOLUTION, resolution)
                putExtra(RecordingService.FPS, fps)
                putExtra(RecordingService.MIC, mic.isChecked)
                putExtra(RecordingService.OVERLAY, overlay.isChecked)
            }
            ContextCompat.startForegroundService(this, i)
            start.isEnabled = false; stop.isEnabled = true; status.text = "Recording..."
        } else if (requestCode == projectionRequest) status.text = "Cancelled"
    }

    private fun stopRecording() {
        startService(Intent(this, RecordingService::class.java).setAction(RecordingService.STOP))
        start.isEnabled = true; stop.isEnabled = false; status.text = "Stopping..."
    }
}
