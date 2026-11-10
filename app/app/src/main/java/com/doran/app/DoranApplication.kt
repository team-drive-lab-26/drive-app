package com.doran.app

import android.app.Application
import com.doran.app.core.data.AppContainer

class DoranApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.tripCoordinator.start()
    }
}
