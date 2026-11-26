#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "HyMT-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxContext = 4096;
constexpr int kPrefillChunk = 256;

struct Engine {
    llama_model * model = nullptr;
    const llama_vocab * vocab = nullptr;
    int threads = 4;
    std::atomic<bool> cancelled{false};
    std::mutex runMutex;
};

void logCallback(enum ggml_log_level level, const char * text, void * /*userData*/) {
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN) {
        LOGE("%s", text);
    } else {
        LOGI("%s", text);
    }
}

// 返回 s 中完整 UTF-8 字符序列的字节长度，末尾被截断的多字节字符不计入
size_t utf8PrefixLength(const std::string & s) {
    size_t i = 0;
    size_t last = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t need;
        if (c < 0x80) {
            need = 1;
        } else if ((c & 0xE0) == 0xC0) {
            need = 2;
        } else if ((c & 0xF0) == 0xE0) {
            need = 3;
        } else if ((c & 0xF8) == 0xF0) {
            need = 4;
        } else {
            i += 1;
            last = i;
            continue;
        }
        if (i + need > s.size()) {
            break;  // 尾部字符不完整，等下一段拼接
        }
        i += need;
        last = i;
    }
    return last;
}

// Java 的 NewStringUTF 只接受 Modified UTF-8，这里手工解码为标准 UTF-16
std::u16string utf8ToUtf16(const std::string & s) {
    std::u16string out;
    size_t i = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        uint32_t cp;
        size_t need;
        if (c < 0x80) {
            cp = c;
            need = 1;
        } else if ((c & 0xE0) == 0xC0) {
            cp = c & 0x1Fu;
            need = 2;
        } else if ((c & 0xF0) == 0xE0) {
            cp = c & 0x0Fu;
            need = 3;
        } else if ((c & 0xF8) == 0xF0) {
            cp = c & 0x07u;
            need = 4;
        } else {
            ++i;
            continue;
        }
        if (i + need > s.size()) {
            break;
        }
        for (size_t k = 1; k < need; ++k) {
            cp = (cp << 6) | (static_cast<unsigned char>(s[i + k]) & 0x3Fu);
        }
        i += need;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(cp));
        }
    }
    return out;
}

bool tokenizeText(const llama_vocab * vocab, const std::string & text, bool addSpecial,
                  std::vector<llama_token> & out) {
    int32_t n = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()),
                               nullptr, 0, addSpecial, true);
    if (n < 0) {
        n = -n;
    }
    if (n == 0) {
        out.clear();
        return true;
    }
    out.resize(static_cast<size_t>(n));
    const int32_t written = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()),
                                           out.data(), n, addSpecial, true);
    if (written < 0) {
        return false;
    }
    out.resize(static_cast<size_t>(written));
    return true;
}

bool decodeTokens(llama_context * ctx, const std::vector<llama_token> & tokens,
                  int startPos, bool logitsOnLast) {
    if (tokens.empty()) {
        return true;
    }
    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    if (batch == nullptr) {
        return false;
    }
    for (size_t i = 0; i < tokens.size(); ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        if (idx < 0) {
            llama_batch_ext_free(batch);
            return false;
        }
        llama_pos pos = static_cast<llama_pos>(startPos + static_cast<int>(i));
        llama_batch_ext_set_pos(batch, idx, &pos);
        llama_batch_ext_set_output_logits(batch, idx, logitsOnLast && (i + 1 == tokens.size()));
    }
    const int32_t rc = llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch);
    llama_batch_ext_free(batch);
    return rc == 0;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_hymt_offline_llm_LlamaBridge_backendInit(JNIEnv * /*env*/, jclass /*clazz*/) {
    static std::once_flag once;
    std::call_once(once, [] {
        llama_log_set(logCallback, nullptr);
        llama_backend_init();
        LOGI("llama.cpp backend initialized");
    });
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hymt_offline_llm_LlamaBridge_loadModel(JNIEnv * env, jclass /*clazz*/,
                                                jstring jpath, jint jthreads) {
    if (jpath == nullptr) {
        return 0;
    }
    const char * cpath = env->GetStringUTFChars(jpath, nullptr);
    if (cpath == nullptr) {
        return 0;
    }

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;  // 纯 CPU 推理

    llama_model * model = llama_model_load_from_file(cpath, params);
    env->ReleaseStringUTFChars(jpath, cpath);

    if (model == nullptr) {
        LOGE("failed to load model");
        return 0;
    }

    auto * engine = new Engine();
    engine->model = model;
    engine->vocab = llama_model_get_vocab(model);
    engine->threads = std::max(1, std::min(static_cast<int>(jthreads), 8));

    LOGI("model loaded, n_ctx_train=%d", llama_model_n_ctx_train(model));
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hymt_offline_llm_LlamaBridge_freeModel(JNIEnv * /*env*/, jclass /*clazz*/, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(engine->runMutex);
        engine->cancelled.store(true);
        if (engine->model != nullptr) {
            llama_model_free(engine->model);
            engine->model = nullptr;
        }
    }
    delete engine;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hymt_offline_llm_LlamaBridge_cancel(JNIEnv * /*env*/, jclass /*clazz*/, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine != nullptr) {
        engine->cancelled.store(true);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_hymt_offline_llm_LlamaBridge_generate(JNIEnv * env, jclass /*clazz*/, jlong handle,
                                               jstring jprompt, jint jmaxTokens,
                                               jfloat jtemperature, jfloat jtopP, jint jtopK,
                                               jfloat jrepeatPenalty, jobject jsink) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    if (engine == nullptr || engine->model == nullptr || engine->vocab == nullptr) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(engine->runMutex);
    engine->cancelled.store(false);

    const char * cprompt = env->GetStringUTFChars(jprompt, nullptr);
    if (cprompt == nullptr) {
        return -1;
    }
    std::string prompt(cprompt);
    env->ReleaseStringUTFChars(jprompt, cprompt);

    // Hy-MT3 对话格式（取自官方 chat_template.jinja，无 system 分支）：
    //   <|hy_begin_of_sentence|><|hy_User|>{user}<|hy_Assistant|>
    // BOS 单独取 token id 拼在最前，避免特殊字符在 tokenize 时被拆开。
    std::vector<llama_token> promptTokens;
    promptTokens.push_back(llama_vocab_bos(engine->vocab));
    {
        std::vector<llama_token> body;
        if (!tokenizeText(engine->vocab, prompt, false, body)) {
            return -2;
        }
        promptTokens.insert(promptTokens.end(), body.begin(), body.end());
    }

    int maxNew = std::max(16, static_cast<int>(jmaxTokens));
    const int budget = kMaxContext - static_cast<int>(promptTokens.size()) - 8;
    if (budget < 16) {
        LOGE("prompt too long: %zu tokens", promptTokens.size());
        return -3;
    }
    maxNew = std::min(maxNew, budget);

    int ctxSize = static_cast<int>(promptTokens.size()) + maxNew + 16;
    ctxSize = std::min(ctxSize, kMaxContext);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(ctxSize);
    cparams.n_batch = static_cast<uint32_t>(std::max(kPrefillChunk * 2, 512));
    cparams.n_ubatch = cparams.n_batch;
    cparams.n_threads = engine->threads;
    cparams.n_threads_batch = engine->threads;

    llama_context * ctx = llama_init_from_model(engine->model, cparams);
    if (ctx == nullptr) {
        LOGE("failed to create context");
        return -4;
    }
    llama_set_n_threads(ctx, engine->threads, engine->threads);

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (jrepeatPenalty > 1.0f) {
        llama_sampler_chain_add(sampler,
            llama_sampler_init_penalties(llama_vocab_n_tokens(engine->vocab), 64,
                                         jrepeatPenalty, 0.0f, 0.0f));
    }
    if (jtopK > 0) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(static_cast<int32_t>(jtopK)));
    }
    if (jtopP > 0.0f && jtopP < 1.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(jtopP, 1));
    }
    if (jtemperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(jtemperature));
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    bool ok = true;
    for (size_t i = 0; i < promptTokens.size(); i += kPrefillChunk) {
        const size_t end = std::min(promptTokens.size(), i + kPrefillChunk);
        std::vector<llama_token> part(promptTokens.begin() + i, promptTokens.begin() + end);
        const bool lastChunk = (end == promptTokens.size());
        if (!decodeTokens(ctx, part, static_cast<int>(i), lastChunk)) {
            LOGE("prefill failed at %zu", i);
            ok = false;
            break;
        }
        if (engine->cancelled.load()) {
            ok = false;
            break;
        }
    }

    jclass sinkClass = env->GetObjectClass(jsink);
    jmethodID onToken = env->GetMethodID(sinkClass, "onToken", "(Ljava/lang/String;)V");
    if (onToken == nullptr) {
        env->ExceptionClear();
    }

    int generated = 0;
    int nextPos = static_cast<int>(promptTokens.size());
    std::string pending;

    while (ok && generated < maxNew) {
        if (engine->cancelled.load()) {
            break;
        }
        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        if (id < 0) {
            break;
        }
        if (llama_vocab_is_eog(engine->vocab, id)) {
            break;
        }
        llama_sampler_accept(sampler, id);

        char piece[512];
        const int n = llama_token_to_piece(engine->vocab, id, piece, sizeof(piece), 0, false);
        if (n > 0) {
            pending.append(piece, static_cast<size_t>(n));
            const size_t valid = utf8PrefixLength(pending);
            if (valid > 0) {
                const std::string emit = pending.substr(0, valid);
                pending.erase(0, valid);
                if (onToken != nullptr) {
                    const std::u16string u16 = utf8ToUtf16(emit);
                    jstring jstr = env->NewString(reinterpret_cast<const jchar *>(u16.data()),
                                                  static_cast<jsize>(u16.size()));
                    if (jstr != nullptr) {
                        env->CallVoidMethod(jsink, onToken, jstr);
                        env->DeleteLocalRef(jstr);
                    }
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                        ok = false;
                        break;
                    }
                }
            }
        }

        ++generated;
        const std::vector<llama_token> one{id};
        if (!decodeTokens(ctx, one, nextPos, true)) {
            break;
        }
        ++nextPos;
    }

    // 输出末尾可能残留未完成的 UTF-8 片段，直接丢弃（极少数情况下为半个字符）
    llama_sampler_free(sampler);
    llama_free(ctx);

    LOGI("generated %d tokens", generated);
    return generated;
}
