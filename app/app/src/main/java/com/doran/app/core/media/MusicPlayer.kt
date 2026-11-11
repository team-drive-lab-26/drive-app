package com.doran.app.core.media

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val catalog = DemoTrackCatalog(appContext)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _nowPlaying = MutableStateFlow<String?>(null)

    /** 마지막으로 튼 곡 ID. 음악 화면에서 "재생 중"을 표시한다 */
    val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()

    fun canPlay(trackId: String): Boolean = catalog.has(trackId)

    fun play(trackId: String) {
        if (!catalog.has(trackId)) return
        _nowPlaying.value = trackId
        withController { controller ->
            // 주소는 서비스가 ID로 채운다
            controller.setMediaItem(MediaItem.Builder().setMediaId(trackId).build())
            controller.prepare()
            controller.play()
        }
    }

    fun stop() {
        _nowPlaying.value = null
        controllerFuture?.takeIf { it.isDone }?.get()?.stop()
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val future = controllerFuture ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, DoranPlaybackService::class.java)),
        ).buildAsync().also { controllerFuture = it }
        future.addListener({ action(future.get()) }, ContextCompat.getMainExecutor(appContext))
    }
}
