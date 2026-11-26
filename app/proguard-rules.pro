# JNI 入口保持原名，避免被混淆
-keepclasseswithmembernames class com.hymt.offline.llm.** {
    native <methods>;
}
-keep class com.hymt.offline.llm.LlamaBridge$TokenSink { *; }
