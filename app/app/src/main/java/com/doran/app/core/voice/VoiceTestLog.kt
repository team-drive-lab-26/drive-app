package com.doran.app.core.voice

import android.util.Log
import com.doran.app.BuildConfig
import org.json.JSONObject

/**
 * 디버그 빌드에서 들은 문장, 호출어 판단, 서버 답을 logcat에 한 줄씩 남긴다.
 * app/scripts/voice_test.py가 이 줄을 읽어 음성 시험 기록(docs/app/voice-test.md)을 만든다.
 */
object VoiceTestLog {
    private const val TAG = "DoranVoiceTest"

    /** [step]은 호출어 대기 중이면 standby, 부른 뒤 듣는 중이면 awake */
    fun heard(step: String, text: String, match: WakeMatch) = write {
        put("type", "heard")
        put("step", step)
        put("text", text)
        put("match", match.javaClass.simpleName)
        if (match is WakeMatch.Command) put("command", match.text)
    }

    fun reply(status: String, caption: String) = write {
        put("type", "reply")
        put("status", status)
        put("caption", caption)
    }

    // 단위 테스트(JVM)에서는 android.util.Log가 없으므로 실패해도 넘어간다
    private fun write(fill: JSONObject.() -> Unit) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.i(TAG, JSONObject().apply(fill).toString()) }
    }
}
