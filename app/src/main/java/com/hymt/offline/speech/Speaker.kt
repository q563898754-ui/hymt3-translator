package com.hymt.offline.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 译文朗读，使用系统 TTS 引擎。离线可用性取决于设备是否已安装对应语言的语音包。
 */
class Speaker(private val context: Context) {

    var onUnavailable: ((String) -> Unit)? = null

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingLocale: String = "en"

    fun speak(text: String, localeTag: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null) {
            pendingText = text
            pendingLocale = localeTag
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    val queued = pendingText
                    val locale = pendingLocale
                    pendingText = null
                    if (queued != null) speakNow(queued, locale)
                } else {
                    onUnavailable?.invoke("系统缺少可用的语音合成引擎")
                }
            }
            return
        }
        if (!ready) {
            pendingText = text
            pendingLocale = localeTag
            return
        }
        speakNow(text, localeTag)
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun release() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun speakNow(text: String, localeTag: String) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onUnavailable?.invoke("未安装 ${locale.displayName} 的离线语音包，无法朗读")
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hymt-utterance")
    }
}
