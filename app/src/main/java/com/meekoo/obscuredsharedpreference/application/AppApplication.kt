package com.meekoo.obscuredsharedpreference.application

import android.app.Application

class AppApplication: Application() {

    companion object {
        lateinit var mApp: AppApplication
    }

    override fun onCreate() {
        super.onCreate()
        mApp = this
    }
}