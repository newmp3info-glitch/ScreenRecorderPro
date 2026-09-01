package com.example.screenrecorderpro

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    companion object { const val START="START"; const val STOP="STOP"; const val RESULT_CODE="result"; const val PROJECTION_DATA="data"; const val RESOLUTION="resolution"; const val FPS="fps"; const val MIC="mic"; const val OVERLAY="overlay"; const val CHANNEL="recording" }
    private var projection: MediaProjection?=null; private var display: VirtualDisplay?=null; private var recorder: MediaRecorder?=null; private var file: File?=null; private var overlayRunning=false
    override fun onCreate(){super.onCreate(); getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Screen recording",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(i:Intent?, flags:Int, id:Int):Int { when(i?.action){START->{val mic=i.getBooleanExtra(MIC,true); val n=Notification.Builder(this,CHANNEL).setContentTitle("Screen Recorder Pro").setContentText("Recording in progress").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build(); if(Build.VERSION.SDK_INT>=29){var type=ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;if(mic)type=type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;startForeground(1,n,type)}else startForeground(1,n); startCapture(i,mic)};STOP->stopCapture()};return START_NOT_STICKY }
    private fun startCapture(i:Intent,mic:Boolean){ if(projection!=null)return; val wm=getSystemService(WINDOW_SERVICE) as android.view.WindowManager; val m=android.util.DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m); val choice=i.getStringExtra(RESOLUTION)?:"Device default"; val size=size(m,choice); val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; projection=pm.getMediaProjection(i.getIntExtra(RESULT_CODE,0),i.getParcelableExtra(PROJECTION_DATA)!!); val dir=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"Recordings").apply{mkdirs()};file=File(dir,"REC_${SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())}.mp4"); recorder=MediaRecorder().apply{if(mic)setAudioSource(MediaRecorder.AudioSource.MIC);setVideoSource(MediaRecorder.VideoSource.SURFACE);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setOutputFile(file!!.absolutePath);setVideoEncoder(MediaRecorder.VideoEncoder.H264);if(mic)setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setVideoSize(size.first,size.second);setVideoFrameRate(i.getIntExtra(FPS,30).coerceIn(15,60));setVideoEncodingBitRate(if(size.first>=3840)35_000_000 else if(size.first>=2560)20_000_000 else 12_000_000);prepare()};display=projection!!.createVirtualDisplay("ScreenRecorderPro",size.first,size.second,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder!!.surface,null,null);recorder!!.start();if(i.getBooleanExtra(OVERLAY,false)){startService(Intent(this,OverlayService::class.java));overlayRunning=true}}
    private fun size(m:android.util.DisplayMetrics,c:String):Pair<Int,Int>{val sw=m.widthPixels;val sh=m.heightPixels;val h=when(c){"1080p"->1080;"1440p"->1440;"2160p (4K)"->2160;else->sh}.coerceAtMost(sh);var w=(h*sw.toFloat()/sh).toInt();w-=w%2;return if(sw>=sh)w to h else h to w}
    private fun stopCapture(){try{recorder?.stop()}catch(_:Exception){file?.delete()};recorder?.reset();recorder?.release();recorder=null;display?.release();display=null;projection?.stop();projection=null;if(overlayRunning){stopService(Intent(this,OverlayService::class.java));overlayRunning=false};stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){if(projection!=null)stopCapture();super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
