package com.hymt.offline

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hymt.offline.data.HistoryItem
import com.hymt.offline.data.HistoryRepository
import com.hymt.offline.data.Lang
import com.hymt.offline.data.Languages
import com.hymt.offline.data.ModelManager
import com.hymt.offline.llm.HyMtEngine
import com.hymt.offline.llm.PromptBuilder
import com.hymt.offline.speech.Speaker
import com.hymt.offline.speech.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface ModelStatus {
    data object Missing : ModelStatus
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Downloading(val done: Long, val total: Long) : ModelStatus
    data class Copying(val done: Long, val total: Long) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

enum class PickerTarget { SOURCE, TARGET }

data class UiState(
    val sourceLang: String = Languages.AUTO,
    val targetLang: String = "en",
    val sourceText: String = "",
    val output: String = "",
    val translating: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.Missing,
    val stats: String? = null,
    val listening: Boolean = false,
    val partial: String = "",
    val history: List<HistoryItem> = emptyList(),
    val message: String? = null,
    val picker: PickerTarget? = null,
    val freeSpaceGb: Double = 0.0,
    val speechIssue: String? = null,
    val autoSpeak: Boolean = true
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = HyMtEngine()
    private val models = ModelManager(app)
    private val historyRepo = HistoryRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            sourceLang = prefs.getString(KEY_SOURCE, Languages.AUTO) ?: Languages.AUTO,
            targetLang = prefs.getString(KEY_TARGET, "en") ?: "en",
            history = historyRepo.load(),
            freeSpaceGb = models.freeSpaceBytes() / 1_000_000_000.0,
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var translateJob: Job? = null
    private var prepareJob: Job? = null
    private var voice: VoiceInput? = null
    private var speaker: Speaker? = null
    private var lastProgressEmit = 0L

    init {
        bootstrap()
    }

    // ---------------------------------------------------------------- 模型

    private fun bootstrap() {
        viewModelScope.launch {
            if (!models.isInstalled()) {
                _state.update { it.copy(modelStatus = ModelStatus.Missing) }
                return@launch
            }
            loadModel()
        }
    }

    private suspend fun loadModel() {
        _state.update { it.copy(modelStatus = ModelStatus.Loading) }
        val file = models.modelFile()
        val result = withContext(Dispatchers.IO) {
            runCatching { engine.load(file) }
        }
        result.onSuccess {
            _state.update { it.copy(modelStatus = ModelStatus.Ready) }
        }.onFailure { e ->
            _state.update {
                it.copy(modelStatus = ModelStatus.Failed(e.message ?: "模型加载失败"))
            }
        }
    }

    fun downloadModel() {
        if (_state.value.modelStatus is ModelStatus.Downloading) return
        if (models.freeSpaceBytes() < REQUIRED_FREE_BYTES) {
            _state.update { it.copy(message = "存储空间不足，至少需要 3 GB 可用空间") }
            return
        }
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            _state.update { it.copy(modelStatus = ModelStatus.Downloading(0L, -1L)) }
            val result = runCatching {
                models.download { done, total -> emitProgress(done, total, false) }
            }
            result.onSuccess { file ->
                loadModelWithProgress(file)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@launch
                _state.update {
                    it.copy(modelStatus = ModelStatus.Failed(e.message ?: "下载失败"))
                }
            }
        }
    }

    fun importModel(uri: Uri) {
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            _state.update { it.copy(modelStatus = ModelStatus.Copying(0L, -1L)) }
            val result = runCatching {
                models.importFrom(uri) { done, total -> emitProgress(done, total, true) }
            }
            result.onSuccess { file ->
                loadModelWithProgress(file)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@launch
                _state.update {
                    it.copy(modelStatus = ModelStatus.Failed(e.message ?: "导入失败"))
                }
            }
        }
    }

    private suspend fun loadModelWithProgress(file: java.io.File) {
        _state.update { it.copy(modelStatus = ModelStatus.Loading) }
        val result = withContext(Dispatchers.IO) { runCatching { engine.load(file) } }
        result.onSuccess {
            _state.update { it.copy(modelStatus = ModelStatus.Ready) }
        }.onFailure { e ->
            _state.update { it.copy(modelStatus = ModelStatus.Failed(e.message ?: "模型加载失败")) }
        }
    }

    fun deleteModel() {
        translateJob?.cancel()
        engine.cancel()
        withContextMain {
            engine.close()
            models.deleteModel()
            _state.update { it.copy(modelStatus = ModelStatus.Missing, output = "", stats = null) }
        }
    }

    private fun emitProgress(done: Long, total: Long, copying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (done > 0 && now - lastProgressEmit < 200 && (total <= 0 || done < total)) return
        lastProgressEmit = now
        _state.update {
            it.copy(
                modelStatus = if (copying) ModelStatus.Copying(done, total)
                else ModelStatus.Downloading(done, total)
            )
        }
    }

    // ---------------------------------------------------------------- 翻译

    fun onSourceTextChange(text: String) {
        _state.update { it.copy(sourceText = text) }
    }

    fun setSharedText(text: String) {
        _state.update { it.copy(sourceText = text.trim()) }
        translate()
    }

    fun openPicker(target: PickerTarget) {
        _state.update { it.copy(picker = target) }
    }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
    }

    fun selectLanguage(code: String) {
        val current = _state.value
        val picker = current.picker ?: return
        val next = if (picker == PickerTarget.SOURCE) {
            prefs.edit().putString(KEY_SOURCE, code).apply()
            current.copy(sourceLang = code, picker = null)
        } else {
            prefs.edit().putString(KEY_TARGET, code).apply()
            current.copy(targetLang = code, picker = null)
        }
        _state.value = next
    }

    fun swapLanguages() {
        val current = _state.value
        if (current.sourceLang == Languages.AUTO) {
            _state.update { it.copy(message = "原文为自动检测时无法对调语言") }
            return
        }
        if (current.sourceLang == current.targetLang) return
        prefs.edit()
            .putString(KEY_SOURCE, current.targetLang)
            .putString(KEY_TARGET, current.sourceLang)
            .apply()
        _state.update {
            it.copy(
                sourceLang = current.targetLang,
                targetLang = current.sourceLang,
                sourceText = it.output.ifBlank { it.sourceText },
                output = ""
            )
        }
    }

    fun translate() {
        val current = _state.value
        if (current.translating) return
        val text = current.sourceText.trim()
        if (text.isEmpty()) return
        if (current.modelStatus != ModelStatus.Ready) {
            _state.update { it.copy(message = "请先准备 Hy-MT3-1.8B 模型") }
            return
        }
        val target: Lang = Languages.of(current.targetLang) ?: return
        val prompt = PromptBuilder.build(target.zh, text)
        val truncated = PromptBuilder.exceedsLimit(text)

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _state.update {
                it.copy(translating = true, output = "", stats = null, message = null,
                    partial = "")
            }
            val builder = StringBuilder()
            val startedAt = SystemClock.elapsedRealtime()
            var deltas = 0

            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    engine.translate(prompt, MAX_NEW_TOKENS) { delta ->
                        deltas += 1
                        builder.append(delta)
                        val snapshot = builder.toString()
                        _state.update { it.copy(output = snapshot) }
                    }
                }
            }

            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            outcome.onSuccess { tokens ->
                val seconds = (elapsedMs / 1000.0).coerceAtLeast(0.001)
                val text = builder.toString().trim()
                val stats = if (text.isEmpty()) null else String.format(
                    Locale.US, "%.1f tok/s · %.1fs · %d 字符",
                    if (deltas > 0) deltas / seconds else 0.0, seconds, text.length
                )
                _state.update {
                    it.copy(
                        translating = false,
                        output = text,
                        stats = stats,
                        message = when {
                            tokens == -3 -> "原文过长，请分段翻译"
                            truncated -> "原文超过 ${PromptBuilder.MAX_SOURCE_CHARS} 字符，已截断"
                            text.isEmpty() -> "模型没有返回译文，可尝试重试"
                            else -> null
                        }
                    )
                }
                if (text.isNotEmpty()) {
                    val item = HistoryItem(
                        id = System.currentTimeMillis(),
                        time = System.currentTimeMillis(),
                        sourceLang = _state.value.sourceLang,
                        targetLang = _state.value.targetLang,
                        source = current.sourceText.trim(),
                        target = text
                    )
                    val merged = historyRepo.add(_state.value.history, item)
                    _state.update { it.copy(history = merged) }
                    if (_state.value.autoSpeak) {
                        speakOutput()
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        translating = false,
                        message = "翻译失败：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun cancelTranslate() {
        engine.cancel()
    }

    fun clearSource() {
        _state.update { it.copy(sourceText = "", output = "", stats = null) }
    }

    fun clearHistory() {
        val empty = historyRepo.clear()
        _state.update { it.copy(history = empty) }
    }

    fun useHistory(item: HistoryItem) {
        _state.update {
            it.copy(
                sourceLang = item.sourceLang,
                targetLang = item.targetLang,
                sourceText = item.source,
                output = item.target,
                stats = null
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---------------------------------------------------------------- 语音

    fun toggleListening(localeTag: String) {
        val current = _state.value
        val input = ensureVoice()
        if (current.listening) {
            input.stop()
            _state.update { it.copy(listening = false, partial = "") }
            return
        }
        _state.update { it.copy(listening = true, partial = "", message = null) }
        input.start(localeTag)
    }

    fun speakOutput() {
        val current = _state.value
        if (current.output.isBlank()) return
        val localeTag = Languages.of(current.targetLang)?.localeTag ?: "en"
        ensureSpeaker().speak(current.output, localeTag)
    }

    /** 对讲机模式的核心开关：译文生成完自动朗读给对方听 */
    fun toggleAutoSpeak() {
        val next = !_state.value.autoSpeak
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, next).apply()
        _state.update { it.copy(autoSpeak = next) }
    }

    private fun ensureVoice(): VoiceInput = voice ?: VoiceInput(getApplication()).also { input ->
        input.onPartial = { text -> _state.update { it.copy(partial = text) } }
        input.onListeningChanged = { listening ->
            _state.update { it.copy(listening = listening) }
        }
        input.onError = { msg ->
            _state.update { it.copy(listening = false, partial = "", message = msg) }
        }
        input.onResult = { text ->
            val merged = listOf(_state.value.sourceText.trim(), text.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            _state.update { it.copy(sourceText = merged, partial = "", listening = false) }
            translate()
        }
        voice = input
    }

    private fun ensureSpeaker(): Speaker = speaker ?: Speaker(getApplication()).also { tts ->
        tts.onIssue = { issue ->
            val msg = when (issue) {
                is Speaker.Issue.NoEngine -> {
                    val detail = if (issue.detected.isEmpty()) {
                        "未检测到任何 TTS 引擎"
                    } else {
                        "检测到：" + issue.detected.joinToString()
                    }
                    "系统里没有可用的语音合成引擎（$detail）。请安装一个语音合成应用（如「Google 文字转语音」或厂商语音服务），再到系统设置中启用。"
                }
                is Speaker.Issue.NoVoice ->
                    "当前语音引擎缺少「${issue.languageName}」的语音包。请到系统「文字转语音」设置中下载该语言的语音数据后重试。"
            }
            _state.update { it.copy(speechIssue = msg) }
        }
        speaker = tts
    }

    fun dismissSpeechIssue() {
        _state.update { it.copy(speechIssue = null) }
    }

    private fun withContextMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) { block() }
    }

    override fun onCleared() {
        super.onCleared()
        voice?.release()
        speaker?.release()
        engine.close()
    }

    companion object {
        private const val PREFS = "hymt_prefs"
        private const val KEY_SOURCE = "lang_source"
        private const val KEY_TARGET = "lang_target"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val MAX_NEW_TOKENS = 1024
        private const val REQUIRED_FREE_BYTES = 3_000_000_000L
    }
}
