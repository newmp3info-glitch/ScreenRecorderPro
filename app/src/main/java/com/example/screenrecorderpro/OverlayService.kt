package com.example.screenrecorderpro

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.TextView

class OverlayService:Service(){
 private var wm:WindowManager?=null;private var view:TextView?=null
 override fun onCreate(){super.onCreate();wm=getSystemService(WINDOW_SERVICE) as WindowManager;view=TextView(this).apply{text="● REC";setTextColor(Color.WHITE);setBackgroundColor(0xCC000000.toInt());setPadding(22,12,22,12);setOnClickListener{stopService(Intent(this@OverlayService,RecordingService::class.java).setAction(RecordingService.STOP));stopSelf()}}
  val type=if(android.os.Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
  val p=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.END;p.x=20;p.y=140;wm?.addView(view,p)
 }
 override fun onDestroy(){view?.let{try{wm?.removeView(it)}catch(_:Exception){}};super.onDestroy()};override fun onBind(i:Intent?):IBinder?=null
}
