package com.hymt.offline.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 语音输入封装：优先使用系统「设备端」识别器（API 31+），确保不联网。
 * 注意：所有方法都必须在主线程调用。
 */
class VoiceInput(private val context: Context) {

    var onPartial: ((String) -> Unit)? = null
    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningChanged: ((Boolean) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** 查询系统里注册的语音识别服务（需在清单声明 queries） */
    fun servicePackages(): List<String> {
        val intent = Intent("android.speech.RecognitionService")
        return runCatching {
            context.packageManager.queryIntentServices(intent, 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
        }.getOrDefault(emptyList())
    }

    fun start(localeTag: String) {
        if (!isAvailable()) {
            val packages = servicePackages()
            val detail = if (packages.isEmpty()) {
                "未检测到任何 RecognitionService"
            } else {
                "检测到：" + packages.joinToString()
            }
            onError?.invoke("系统未提供语音识别服务（$detail）")
            return
        }
        releaseRecognizer()
        val created = createRecognizer()
        if (created == null) {
            onError?.invoke("无法启动设备端语音识别")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { created.startListening(intent) }
            .onFailure {
                onError?.invoke("语音识别启动失败：${it.message ?: "未知错误"}")
                onListeningChanged?.invoke(false)
            }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun release() {
        releaseRecognizer()
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                if (r != null) {
                    onDevice = true
                    return r
                }
            }
        }
        onDevice = false
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    private fun releaseRecognizer() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListeningChanged?.invoke(true)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            onListeningChanged?.invoke(false)
            onError?.invoke(describe(error))
        }

        override fun onResults(results: Bundle?) {
            onListeningChanged?.invoke(false)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (text.isNullOrBlank()) {
                onError?.invoke("没有识别到语音，请重试")
            } else {
                onResult?.invoke(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onPartial?.invoke(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "录音出错"
        SpeechRecognizer.ERROR_CLIENT -> "识别客户端异常"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
        SpeechRecognizer.ERROR_NETWORK -> "离线识别不可用，已尝试联网（被网络策略阻止）"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配的识别结果"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别引擎忙，请稍后再试"
        SpeechRecognizer.ERROR_SERVER -> "识别服务不可用"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音输入"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "系统未安装该语言的离线语音包"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "该语言的离线识别暂不可用"
        else -> "语音识别失败（$error）"
    }
}
