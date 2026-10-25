package com.mecnun.assistant

import android.app.Application

class MecnunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        History.init(this)
        Notes.init(this)
    }
}
