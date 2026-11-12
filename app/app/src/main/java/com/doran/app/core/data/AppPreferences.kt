package com.doran.app.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Preferences(
    val onboarded: Boolean = false,
    /** 녹음 뒤 "누가 말했어?"에 보여 줄 이름 */
    val family: List<String> = DEFAULT_FAMILY,
    /** 꺼 두면 "도란아" 대기 없이 도란을 눌러야만 듣는다 */
    val wakeWord: Boolean = true,
    /** 켜 두면 도란이 먼저 제안하지 않는다 */
    val quiet: Boolean = false,
    /** 화면 밝기. auto(전조등, 값이 없으면 07~19시 낮), day, night */
    val screen: String = SCREEN_AUTO,
) {
    companion object {
        val DEFAULT_FAMILY = listOf("엄마", "아빠", "아이")
        const val SCREEN_AUTO = "auto"
        const val SCREEN_DAY = "day"
        const val SCREEN_NIGHT = "night"
    }
}

/** 기기에 남기는 설정. 서버로 보내지 않는다 */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("doran", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Preferences> = _state.asStateFlow()

    fun update(transform: (Preferences) -> Preferences) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(KEY_ONBOARDED, next.onboarded)
            .putString(KEY_FAMILY, next.family.joinToString("\n"))
            .putBoolean(KEY_WAKE_WORD, next.wakeWord)
            .putBoolean(KEY_QUIET, next.quiet)
            .putString(KEY_SCREEN, next.screen)
            .apply()
        _state.value = next
    }

    private fun read() = Preferences(
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
        family = prefs.getString(KEY_FAMILY, null)
            ?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty)
            ?.ifEmpty { null } ?: Preferences.DEFAULT_FAMILY,
        wakeWord = prefs.getBoolean(KEY_WAKE_WORD, true),
        quiet = prefs.getBoolean(KEY_QUIET, false),
        screen = prefs.getString(KEY_SCREEN, null) ?: Preferences.SCREEN_AUTO,
    )

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_FAMILY = "family"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_QUIET = "quiet"
        const val KEY_SCREEN = "screen"
    }
}
