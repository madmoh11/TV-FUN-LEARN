package com.mohammad.tvdraw

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}
