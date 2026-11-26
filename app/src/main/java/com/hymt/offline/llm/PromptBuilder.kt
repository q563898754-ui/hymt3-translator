package com.hymt.offline.llm

/**
 * Hy-MT3 的提示词构造。
 *
 * 对话格式取自官方 chat_template.jinja（无 system 分支）：
 * `<|hy_begin_of_sentence|><|hy_User|>{user}<|hy_Assistant|>`
 * 其中 BOS 由原生层用 token id 补在最前，这里只负责 user/assistant 段。
 */
object PromptBuilder {

    private const val USER_TAG = "<\uFF5Chy_User\uFF5C>"
    private const val ASSISTANT_TAG = "<\uFF5Chy_Assistant\uFF5C>"

    /** 模型上下文 8192，留出译文空间后限制原文长度 */
    const val MAX_SOURCE_CHARS = 2400

    fun build(targetLanguageZh: String, sourceText: String): String {
        val text = sourceText.trim().take(MAX_SOURCE_CHARS)
        val instruction = "将以下文本翻译为$targetLanguageZh，注意只需要输出翻译后的结果，不要额外解释：\n\n$text"
        return USER_TAG + instruction + ASSISTANT_TAG
    }

    fun exceedsLimit(sourceText: String): Boolean = sourceText.length > MAX_SOURCE_CHARS
}
