package com.doran.app.core.data

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

class PleosWaypointController(context: Context, private val onNaviError: () -> Unit) {
    private val helper = NaviHelper(context.applicationContext)
    private var initialized = false
    private val listener = object : NaviHelperEventListener {
        override fun onError(errorCode: NaviErrorCode) {
            onNaviError()
        }
    }

    fun add(request: WaypointRequest, onSubmitted: (Boolean) -> Unit) {
        if (initialized) {
            submit(request, onSubmitted)
            return
        }
        try {
            helper.initialize(
                onConnected = {
                    helper.addListener(listener)
                    initialized = true
                    submit(request, onSubmitted)
                },
                onFailed = { onSubmitted(false) },
            )
        } catch (_: Exception) {
            onSubmitted(false)
        }
    }

    private fun submit(request: WaypointRequest, onSubmitted: (Boolean) -> Unit) {
        try {
            helper.addWaypoint(
                RequestWaypointInfo(
                    latitude = request.latitude,
                    longitude = request.longitude,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = request.naviPoiId,
                    poiName = request.name,
                    poiSubId = request.naviPoiSubId,
                    address = request.address,
                ),
            )
            onSubmitted(true)
        } catch (_: Exception) {
            onSubmitted(false)
        }
    }

    fun release() {
        if (initialized) {
            helper.removeListener(listener)
            helper.release()
        }
        initialized = false
    }
}
