package com.hymt.offline.llm

import java.io.File

/**
 * Hy-MT3-1.8B 的本地推理引擎（llama.cpp 后端）。
 *
 * 单实例、串行推理：一次只允许一个翻译任务，重复调用会由原生层的互斥锁排队。
 */
class HyMtEngine {

    @Volatile
    private var handle: Long = 0L

    val isLoaded: Boolean
        get() = handle != 0L

    /** 加载 GGUF 模型，耗时约 1~3 秒，必须在后台线程调用。 */
    fun load(modelFile: File, threads: Int = defaultThreads()) {
        if (handle != 0L) return
        require(modelFile.exists()) { "模型文件不存在" }
        LlamaBridge.backendInit()
        val h = LlamaBridge.loadModel(modelFile.absolutePath, threads)
        check(h != 0L) { "模型加载失败，请确认文件完整（Hy-MT3-1.8B-Q4_K_M.gguf 约 1.35 GB）" }
        handle = h
    }

    fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) {
            LlamaBridge.freeModel(h)
        }
    }

    fun cancel() {
        val h = handle
        if (h != 0L) LlamaBridge.cancel(h)
    }

    /**
     * 阻塞式流式翻译，必须在后台线程调用。
     *
     * @return 生成的 token 数量，负值为原生层错误码
     */
    fun translate(prompt: String, maxTokens: Int, onDelta: (String) -> Unit): Int {
        val h = handle
        check(h != 0L) { "模型尚未加载" }
        val sink = object : LlamaBridge.TokenSink {
            override fun onToken(text: String) {
                onDelta(text)
            }
        }
        return LlamaBridge.generate(
            h,
            prompt,
            maxTokens,
            TEMPERATURE,
            TOP_P,
            TOP_K,
            REPEAT_PENALTY,
            sink
        )
    }

    companion object {
        // 官方推荐的 1.8B 推理参数
        private const val TEMPERATURE = 0.7f
        private const val TOP_P = 0.6f
        private const val TOP_K = 20
        private const val REPEAT_PENALTY = 1.05f

        fun defaultThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores / 2).coerceIn(2, 6)
        }
    }
}
