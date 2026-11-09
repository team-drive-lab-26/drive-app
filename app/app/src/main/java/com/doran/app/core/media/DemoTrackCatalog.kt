package com.doran.app.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * 앱에 넣어 둔 시연용 음원. 파일 이름이 서버 곡 목록의 track_id와 같아야 한다.
 * 예: assets/music/demo-track-1.mp3 ↔ tracks.json의 "demo-track-1"
 */
class DemoTrackCatalog(private val context: Context) {

    fun ids(): List<String> =
        context.assets.list(DIR).orEmpty()
            .filter { name -> EXTENSIONS.any { name.endsWith(it) } }
            .map { it.substringBeforeLast('.') }
            .sorted()

    fun has(trackId: String): Boolean = fileName(trackId) != null

    fun mediaItem(trackId: String): MediaItem? {
        val file = fileName(trackId) ?: return null
        val tags = readTags(file)
        return MediaItem.Builder()
            .setMediaId(trackId)
            .setUri(Uri.parse("asset:///$DIR/$file"))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(tags.first ?: trackId)
                    .setArtist(tags.second)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    private fun fileName(trackId: String): String? =
        context.assets.list(DIR).orEmpty().firstOrNull { name ->
            name.substringBeforeLast('.') == trackId && EXTENSIONS.any { name.endsWith(it) }
        }

    // 차 화면 플레이어에 곡 제목이 뜨도록 음원 파일의 태그를 읽는다
    private fun readTags(file: String): Pair<String?, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            context.assets.openFd("$DIR/$file").use { fd ->
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) to
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } catch (_: Exception) {
            null to null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val DIR = "music"
        val EXTENSIONS = listOf(".mp3", ".m4a", ".ogg", ".wav")
    }
}
