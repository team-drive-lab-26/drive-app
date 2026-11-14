package com.doran.app.core.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 목소리 캡슐 한 개. 파일은 기기에만 두고 서버에는 ID와 남긴 사람만 보낸다 */
@Serializable
data class Capsule(
    val id: String,
    val sessionId: String,
    val speaker: String,
    val placeName: String,
    val createdAt: Long,
    val durationMs: Long,
)

/** 캡슐 녹음 파일과 목록. 목록은 capsules.json 한 파일에 둔다 */
class CapsuleStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "capsules").apply { mkdirs() }
    private val index = File(dir, "capsules.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _capsules = MutableStateFlow(read())
    val capsules: StateFlow<List<Capsule>> = _capsules.asStateFlow()

    fun newId(): String = "cap-" + UUID.randomUUID().toString().take(12)

    fun file(id: String): File = File(dir, "$id.m4a")

    fun has(id: String): Boolean = file(id).let { it.exists() && it.length() > 0 }

    fun save(capsule: Capsule) {
        val next = _capsules.value.filterNot { it.id == capsule.id } + capsule
        index.writeText(json.encodeToString(ListSerializer(Capsule.serializer()), next))
        _capsules.value = next
    }

    fun delete(id: String) {
        file(id).delete()
        val next = _capsules.value.filterNot { it.id == id }
        index.writeText(json.encodeToString(ListSerializer(Capsule.serializer()), next))
        _capsules.value = next
    }

    private fun read(): List<Capsule> = runCatching {
        json.decodeFromString(ListSerializer(Capsule.serializer()), index.readText())
    }.getOrDefault(emptyList())
}

/** 15초 녹음기. 소리 크기를 0~1로 알려 줘 도란이 듣는 모습에 쓴다 */
class CapsuleRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(file: File): Boolean {
        stop()
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(MAX_MS.toInt())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
        }.onFailure { created.release() }.isSuccess
    }

    /** 0~1. 녹음 중이 아니면 0 */
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 20_000f }.getOrDefault(0f).coerceIn(0f, 1f)

    fun stop() {
        val current = recorder ?: return
        recorder = null
        runCatching { current.stop() }
        current.release()
    }

    companion object {
        const val MAX_MS = 15_000L
    }
}

/** 캡슐 재생. 한 번에 하나만 튼다 */
class CapsulePlayer(private val store: CapsuleStore) {
    private var player: MediaPlayer? = null
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    fun toggle(id: String) {
        if (_playingId.value == id) stop() else play(id)
    }

    fun play(id: String, onDone: () -> Unit = {}): Boolean {
        stop()
        if (!store.has(id)) return false
        val created = MediaPlayer()
        return runCatching {
            created.setDataSource(store.file(id).absolutePath)
            created.setOnCompletionListener {
                stop()
                onDone()
            }
            created.prepare()
            created.start()
            player = created
            _playingId.value = id
        }.onFailure { created.release() }.isSuccess
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playingId.value = null
    }
}
