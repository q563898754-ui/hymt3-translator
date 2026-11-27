# Hy 翻译 · 基于 Hy-MT3 的安卓离线翻译 App

用腾讯混元开源的 [Hy-MT3-1.8B](https://huggingface.co/tencent/Hy-MT3-1.8B-GGUF)（Apache-2.0）做本地推理的安卓翻译应用：
模型权重全部留在手机上，翻译过程不发起任何网络请求。

## 能力

| 项 | 说明 |
| --- | --- |
| 翻译 | 102 种语言互译，目标语言用官方推荐的中文提示词 |
| 模型 | Hy-MT3-1.8B Q4_K_M（1.35 GB），8 GB 内存机型可跑 |
| 推理 | llama.cpp（源码集成，NDK 编译，纯 CPU，arm64-v8a） |
| 语音 | 语音输入走系统「设备端」识别（API 31+ 优先，强制 prefer-offline）；译文朗读走系统 TTS |
| 界面 | Jetpack Compose，深色中控屏风格，手机单列 / 平板三栏自适应，语言宫格选择器 |
| 其他 | 流式输出、tok/s 统计、历史记录、系统分享入口、断点续传下载 |

## 构建

### 方式一：GitHub Actions（推荐，无需本地环境）

1. 新建仓库并推送本目录。
2. Actions 会自动执行 `.github/workflows/android.yml`。
3. 在 Actions 产物里下载 `hymt3-translator-debug`，内含 `app-debug.apk`。
4. `adb install -r app-debug.apk`。

首次构建需要编译 llama.cpp，约 10–20 分钟；后续构建会复用 Gradle 缓存。

### 方式二：Android Studio

1. 用 Android Studio 打开 `hymt-android` 目录。
2. 首次同步会自动下载 Gradle 8.9 / AGP 8.7.3 / NDK 27.0.12077973 / CMake 3.22.1。
3. 直接 Run，或 `Build > Build APK(s)`。

命令行等价：

```bash
./gradlew :app:assembleDebug
```

构建期会从 GitHub 拉取 llama.cpp 源码（`app/src/main/cpp/CMakeLists.txt` 中的 `FetchContent`）。
需要可复现构建时，把 `GIT_TAG master` 固定到具体 tag 即可。

## 模型准备

APK 不内置模型。首次打开应用有三种方式：

1. **应用内下载**：优先从 ModelScope（魔搭）拉取 `Hy-MT3-1.8B-Q4_K_M.gguf`，失败再回退 Hugging Face 与 hf-mirror，支持断点续传。国内网络通常无需代理。
2. **本地导入**：用系统文件选择器选中已下载的 `.gguf`。
3. **手动推入**（需 adb）：

```bash
adb push Hy-MT3-1.8B-Q4_K_M.gguf /data/local/tmp/
adb shell run-as com.hymt.offline mkdir -p files/models
adb shell "run-as com.hymt.offline cp /data/local/tmp/Hy-MT3-1.8B-Q4_K_M.gguf files/models/"
```

模型文件路径：`/data/data/com.hymt.offline/files/models/Hy-MT3-1.8B-Q4_K_M.gguf`

## 代码结构

```
app/src/main/cpp/            llama.cpp 集成 + JNI（流式解码、UTF-8 分片处理）
app/src/main/java/com/hymt/offline/
  llm/                       LlamaBridge.java（native 声明）、HyMtEngine、PromptBuilder
  data/                      102 种语言表、模型下载/导入、历史记录
  speech/                    语音输入（设备端识别）、TTS 朗读
  ui/                        主题、通用组件、翻译主界面、语言宫格、模型准备页
  AppViewModel.kt            状态机：模型生命周期、翻译任务、语音
```

## 与官方的对齐点

- 对话模板取自 `chat_template.jinja`：`<|hy_begin_of_sentence|><|hy_User|>…<|hy_Assistant|>`
- 采样参数采用官方 1.8B 推荐值：temperature 0.7 / top_p 0.6 / top_k 20 / repetition_penalty 1.05
- 提示词采用官方默认翻译指令：`将以下文本翻译为X，注意只需要输出翻译后的结果，不要额外解释：`

## 已知限制

- **仅支持 arm64-v8a**：1.35 GB 模型在 32 位设备上没有实用价值，已裁剪其他 ABI。
- **内存**：加载后常驻约 1.5–1.8 GB（含 2048 token 的 KV 缓存），建议 8 GB 及以上机型；低内存机型可能被系统回收。
- **语音识别不是 100% 自包含**：走 Android 系统识别器并强制 `EXTRA_PREFER_OFFLINE`，能否真正离线取决于设备是否安装了对应语言的离线语音包。若要完全自包，需要再集成 whisper.cpp（可作为后续扩展，当前版本未包含）。
- **朗读依赖系统语音引擎**：中文/英文由系统引擎本地朗读；乌兹别克语等小语种需要安装支持该语言的引擎（例如开源的 RHVoice），并在系统「文字转语音」中设为首选引擎。应用本身不联网朗读。
- **单次输入上限 2400 字符**：受上下文预算约束，超出会截断并提示。
- **llama.cpp 跟随 master**：Hy-MT3 属较新架构，构建时取最新源码；如需锁定版本请改 `GIT_TAG`。

## 许可

- 应用代码：可自由修改使用。
- 模型权重：Hy-MT3 由腾讯混元开源，Apache-2.0，使用时请遵守其模型卡说明。
