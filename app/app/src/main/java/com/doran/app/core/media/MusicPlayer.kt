package com.doran.app.core.media

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val catalog = DemoTrackCatalog(appContext)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _nowPlaying = MutableStateFlow<String?>(null)
    private val _paused = MutableStateFlow(false)
    private val _library = MutableStateFlow<List<LocalTrack>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { _library.value = catalog.tracks() }
    }

    /** 일시정지 중인지. 음악 화면 재생 버튼 모양을 정한다 */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** 기기에 넣어 둔 곡 전체 */
    val library: StateFlow<List<LocalTrack>> = _library.asStateFlow()

    /** 마지막으로 튼 곡 ID. 음악 화면에서 "재생 중"을 표시한다 */
    val nowPlaying: StateFlow<String?> = _nowPlaying.asStateFlow()

    fun canPlay(trackId: String): Boolean = catalog.has(trackId)

    fun play(trackId: String) {
        if (!catalog.has(trackId)) return
        _nowPlaying.value = trackId
        _paused.value = false
        withController { controller ->
            // 주소는 서비스가 ID로 채운다
            controller.setMediaItem(MediaItem.Builder().setMediaId(trackId).build())
            controller.prepare()
            controller.play()
        }
    }

    fun pause() {
        if (_nowPlaying.value == null) return
        _paused.value = true
        controllerFuture?.takeIf { it.isDone }?.get()?.pause()
    }

    fun resume() {
        val id = _nowPlaying.value ?: return
        _paused.value = false
        val controller = controllerFuture?.takeIf { it.isDone }?.get()
        if (controller != null && controller.mediaItemCount > 0) controller.play() else play(id)
    }

    fun stop() {
        _nowPlaying.value = null
        _paused.value = false
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
