package com.hymt.offline.llm;

/**
 * llama.cpp 的最小 JNI 封装。所有方法均为阻塞调用，必须在后台线程执行。
 */
public final class LlamaBridge {

    static {
        System.loadLibrary("hymt");
    }

    private LlamaBridge() {
    }

    /** 逐 token 回调；实现方需保证线程安全（回调来自推理线程）。 */
    public interface TokenSink {
        void onToken(String text);
    }

    public static native void backendInit();

    /** @return 引擎句柄，0 表示加载失败 */
    public static native long loadModel(String path, int threads);

    public static native void freeModel(long handle);

    public static native void cancel(long handle);

    /**
     * 流式生成译文。
     *
     * @return 生成的 token 数；负值代表错误（-1 句柄无效 / -2 分词失败 / -3 文本过长 / -4 上下文创建失败）
     */
    public static native int generate(long handle, String prompt, int maxTokens,
                                      float temperature, float topP, int topK,
                                      float repeatPenalty, TokenSink sink);
}
