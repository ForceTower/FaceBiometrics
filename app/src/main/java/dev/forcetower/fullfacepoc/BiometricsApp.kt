package dev.forcetower.fullfacepoc

import android.app.Application
import timber.log.Timber

class BiometricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}