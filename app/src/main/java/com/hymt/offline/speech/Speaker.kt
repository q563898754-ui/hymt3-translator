package com.hymt.offline.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 译文朗读，使用系统 TTS 引擎。
 *
 * 国内 ROM（含 HyperOS）常见两种情况：
 * 1. 没有任何 TTS 引擎 —— 初始化失败
 * 2. 有引擎但缺少对应语言的语音包 —— setLanguage 返回 LANG_MISSING_DATA
 * 两种情况都会通过 onIssue 上报，由界面引导用户去系统「文字转语音」设置。
 */
class Speaker(private val context: Context) {

    sealed interface Issue {
        data object NoEngine : Issue
        data class NoVoice(val languageName: String) : Issue
    }

    var onIssue: ((Issue) -> Unit)? = null

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
                if (!ready) {
                    onIssue?.invoke(Issue.NoEngine)
                    return@TextToSpeech
                }
                val queued = pendingText
                val locale = pendingLocale
                pendingText = null
                if (queued != null) speakNow(queued, locale)
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

    /** 引擎是否已就绪（用于界面提示当前是否可朗读） */
    fun isReady(): Boolean = ready

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
        if (!applyLanguage(engine, locale)) {
            onIssue?.invoke(Issue.NoVoice(localeName(locale)))
            return
        }
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zrc-utterance")
        }.onFailure {
            onIssue?.invoke(Issue.NoVoice(localeName(locale)))
        }
    }

    /**
     * 逐个尝试候选 Locale，只要有一个可用就返回 true。
     * 中文设备上 "zh-CN" / "zh_CN" / "zh" 的语音包注册方式并不统一。
     */
    private fun applyLanguage(engine: TextToSpeech, locale: Locale): Boolean {
        val candidates = linkedSetOf<Locale>()
        candidates.add(locale)
        when (locale.language) {
            "zh" -> {
                candidates.add(Locale.SIMPLIFIED_CHINESE)
                candidates.add(Locale.CHINA)
                candidates.add(Locale.forLanguageTag("zh"))
                candidates.add(Locale.TRADITIONAL_CHINESE)
            }

            "en" -> {
                candidates.add(Locale.US)
                candidates.add(Locale.UK)
                candidates.add(Locale.ENGLISH)
            }
        }
        for (candidate in candidates) {
            val result = runCatching { engine.setLanguage(candidate) }.getOrDefault(-1)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
    }

    private fun localeName(locale: Locale): String =
        locale.getDisplayName(Locale.CHINA).ifBlank { locale.toLanguageTag() }
}
