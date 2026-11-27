package com.hymt.offline.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 译文朗读。
 *
 * 引擎策略（按目标语言自动切换）：
 *  - 中文 / 英文：用系统默认引擎（音质最好，离线）
 *  - 其它语种：优先用 RHVoice（它带乌兹别克语等小语种语音包），失败再退回默认引擎
 *
 * 这样即使用户把 RHVoice 设成系统默认，也不会出现「中文读不出来」的问题。
 */
class Speaker(private val context: Context) {

    sealed interface Issue {
        data class NoEngine(val detected: List<String>) : Issue
        data class NoVoice(val languageName: String) : Issue
    }

    var onIssue: ((Issue) -> Unit)? = null

    private var engine: TextToSpeech? = null
    private var enginePackage: String? = null
    private var ready = false
    private var retriedWithDefault = false
    private var pending: Pair<String, String>? = null

    fun speak(text: String, localeTag: String) {
        if (text.isBlank()) return
        pending = text to localeTag
        retriedWithDefault = false
        ensureEngine()
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    fun release() {
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    private fun ensureEngine() {
        val job = pending ?: return
        val locale = Locale.forLanguageTag(job.second)
        val wantPackage = if (needsThirdParty(locale)) thirdPartyPackage() else null

        val current = engine
        if (current != null && ready && enginePackage == wantPackage) {
            deliver(current, job)
            return
        }
        create(wantPackage)
    }

    private fun create(packageName: String?) {
        destroyCurrent()
        enginePackage = packageName
        ready = false

        val holder = arrayOfNulls<TextToSpeech>(1)
        val listener = TextToSpeech.OnInitListener { status ->
            val created = holder[0]
            if (status == TextToSpeech.SUCCESS && created != null) {
                engine = created
                ready = true
                ensureEngine()
            } else {
                created?.let { runCatching { it.shutdown() } }
                if (packageName != null && !retriedWithDefault) {
                    retriedWithDefault = true
                    create(null)
                } else {
                    pending = null
                    onIssue?.invoke(Issue.NoEngine(enginePackages()))
                }
            }
        }

        val created = runCatching {
            if (packageName != null) {
                TextToSpeech(context, listener, packageName)
            } else {
                TextToSpeech(context, listener)
            }
        }.getOrNull()
        holder[0] = created

        if (created == null) {
            if (packageName != null && !retriedWithDefault) {
                retriedWithDefault = true
                create(null)
            } else {
                pending = null
                onIssue?.invoke(Issue.NoEngine(enginePackages()))
            }
        }
    }

    private fun deliver(target: TextToSpeech, job: Pair<String, String>) {
        val locale = Locale.forLanguageTag(job.second)
        if (applyLanguage(target, locale)) {
            pending = null
            runCatching { target.speak(job.first, TextToSpeech.QUEUE_FLUSH, null, "zrc-utterance") }
            return
        }
        // 第三方引擎没有该语言 → 退回系统默认引擎再试一次
        if (enginePackage != null && !retriedWithDefault) {
            retriedWithDefault = true
            create(null)
        } else {
            pending = null
            onIssue?.invoke(Issue.NoVoice(localeName(locale)))
        }
    }

    private fun destroyCurrent() {
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    private fun needsThirdParty(locale: Locale): Boolean =
        locale.language != "zh" && locale.language != "en"

    /** 已安装的 RHVoice 引擎包名；没有则返回 null */
    private fun thirdPartyPackage(): String? =
        enginePackages().firstOrNull { it.contains("rhvoice", ignoreCase = true) }

    /** 查询系统里注册的 TTS 引擎（清单已声明 queries） */
    fun enginePackages(): List<String> {
        val intent = Intent("android.intent.action.TTS_SERVICE")
        return runCatching {
            context.packageManager.queryIntentServices(intent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
        }.getOrDefault(emptyList())
    }

    /**
     * 逐个尝试候选 Locale，只要有一个可用就返回 true。
     * 中文设备上 "zh-CN" / "zh_CN" / "zh" 的语音包注册方式并不统一。
     */
    private fun applyLanguage(target: TextToSpeech, locale: Locale): Boolean {
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
            val result = runCatching { target.setLanguage(candidate) }.getOrDefault(-1)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
    }

    private fun localeName(locale: Locale): String =
        locale.getDisplayName(Locale.CHINA).ifBlank { locale.toLanguageTag() }
}
