package com.hymt.offline.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型文件的下载 / 导入 / 校验。
 * 应用内不打包模型，避免 APK 体积过大（Q4_K_M 约 1.35 GB）。
 */
class ModelManager(private val context: Context) {

    fun modelDir(): File = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun modelFile(): File = File(modelDir(), FILE_NAME)

    fun isInstalled(): Boolean = modelFile().let { it.exists() && it.length() > MIN_BYTES }

    fun freeSpaceBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes

    /** 依次尝试多个源，支持断点续传。 */
    suspend fun download(onProgress: (downloaded: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val part = File(modelDir(), "$FILE_NAME.part")
            var lastError: Exception? = null
            for (source in SOURCES) {
                try {
                    fetch(source, part, onProgress)
                    if (part.length() > MIN_BYTES) {
                        return@withContext promote(part)
                    }
                    part.delete()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IOException("模型下载失败：${lastError?.message ?: "网络不可用"}", lastError)
        }

    /** 从系统文件选择器导入本地 GGUF。 */
    suspend fun importFrom(uri: Uri, onProgress: (downloaded: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val part = File(modelDir(), "$FILE_NAME.part")
            val total = querySize(uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法读取所选文件")
            input.use { source ->
                FileOutputStream(part, false).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
            if (part.length() <= MIN_BYTES) {
                part.delete()
                throw IOException("文件不完整，或不是有效的 Hy-MT3 GGUF 模型")
            }
            promote(part)
        }

    fun deleteModel() {
        modelFile().delete()
        File(modelDir(), "$FILE_NAME.part").delete()
    }

    private fun promote(part: File): File {
        val target = modelFile()
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return target
    }

    private fun fetch(url: String, part: File, onProgress: (Long, Long) -> Unit) {
        var existing = if (part.exists()) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HyMT-Android/0.1")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                existing = 0L
                part.delete()
            } else if (code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP $code")
            }
            val length = conn.contentLengthLong
            val total = if (length > 0) existing + length else -1L
            conn.inputStream.use { input ->
                FileOutputStream(part, existing > 0).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var done = existing
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("下载已取消")
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun querySize(uri: Uri): Long {
        val projection = arrayOf(OpenableColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0)
            }
        }
        return -1L
    }

    companion object {
        const val FILE_NAME = "Hy-MT3-1.8B-Q4_K_M.gguf"

        private const val MIN_BYTES = 1_000_000_000L

        private const val MODEL_SCOPE_URL =
            "https://modelscope.cn/models/Tencent-Hunyuan/Hy-MT3-1.8B-GGUF/resolve/master/Hy-MT3-1.8B-Q4_K_M.gguf"

        private const val HF_URL =
            "https://huggingface.co/tencent/Hy-MT3-1.8B-GGUF/resolve/main/Hy-MT3-1.8B-Q4_K_M.gguf"

        private const val HF_MIRROR_URL =
            "https://hf-mirror.com/tencent/Hy-MT3-1.8B-GGUF/resolve/main/Hy-MT3-1.8B-Q4_K_M.gguf"

        /** 国内优先走 ModelScope（魔搭），失败再回退 HuggingFace 及其镜像 */
        private val SOURCES = listOf(MODEL_SCOPE_URL, HF_URL, HF_MIRROR_URL)
    }
}
