package com.callonlines.softphone

import android.app.Application

class SoftphoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinphoneManager.init(this)
    }
}
