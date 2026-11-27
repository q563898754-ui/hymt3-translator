package com.hymt.offline.speech

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Edge 浏览器的「大声朗读」接口，免费、无需 API Key。
 *
 * 注意：这是社区逆推出的非官方接口，微软随时可能调整协议导致失效；
 * 仅适合个人自用，不能作为稳定服务依赖。
 */
class EdgeTts(private val context: Context) {

    data class Voice(val name: String, val locale: String, val gender: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null
    private var cachedVoices: List<Voice>? = null

    /** 合成并播放；langCode 使用我们语言表里的代码，如 uz / en / zh */
    suspend fun speak(text: String, langCode: String) {
        val mp3 = synthesize(text, langCode)
        playFile(mp3)
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    fun release() {
        stop()
    }

    private suspend fun synthesize(text: String, langCode: String): File = withContext(Dispatchers.IO) {
        val voice = pickVoice(langCode)
        val audio = ByteArrayOutputStream()
        val latch = CountDownLatch(1)
        var failure: Throwable? = null

        val request = Request.Builder()
            .url(wsUrl())
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("User-Agent", USER_AGENT)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(configMessage())
                webSocket.send(ssmlMessage(text, voice))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size < 2) return
                val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                if (data.size < 2 + headerLength) return
                val header = String(data, 2, headerLength, Charsets.UTF_8)
                if (header.contains("Path:audio")) {
                    audio.write(data, 2 + headerLength, data.size - 2 - headerLength)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = t
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                latch.countDown()
            }
        }

        val socket = http.newWebSocket(request, listener)
        val finished = latch.await(45, TimeUnit.SECONDS)
        socket.cancel()

        if (!finished) throw IOException("云端语音超时")
        failure?.let { throw IOException("云端语音连接失败：${it.message}", it) }

        val bytes = audio.toByteArray()
        if (bytes.isEmpty()) throw IOException("云端没有返回音频")

        val dir = File(context.cacheDir, "edge_tts").apply { mkdirs() }
        val file = File(dir, "speech.mp3")
        file.writeBytes(bytes)
        file
    }

    private suspend fun playFile(file: File) = withContext(Dispatchers.Main) {
        stop()
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.setOnCompletionListener {
            it.release()
            if (player === it) player = null
        }
        mp.prepare()
        mp.start()
        player = mp
    }

    private fun pickVoice(langCode: String): Voice {
        val voices = cachedVoices ?: fetchVoices().also { cachedVoices = it }
        val prefix = when (langCode) {
            "zh-TR" -> "zh-tw"
            "yue" -> "zh-hk"
            "tl" -> "fil"
            "mn" -> "mn-mn"
            "kk" -> "kk-kz"
            else -> langCode.lowercase(Locale.US)
        }
        val matched = voices.filter {
            it.locale.lowercase(Locale.US).startsWith(prefix.take(2)) ||
                it.locale.lowercase(Locale.US).startsWith(prefix)
        }
        val chosen = matched.firstOrNull { it.gender.equals("Female", true) } ?: matched.firstOrNull()
        if (chosen != null) return chosen

        FALLBACK_VOICES[langCode]?.let { name ->
            return Voice(name, langCode, "Female")
        }
        throw IOException("Edge 语音暂不支持该语言（$langCode）")
    }

    private fun fetchVoices(): List<Voice> {
        val url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list" +
            "?trustedclienttoken=$TRUSTED_TOKEN" +
            "&Sec-MS-GEC=${secMsGec()}" +
            "&Sec-MS-GEC-Version=${SEC_VERSION}"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("语音列表请求失败 HTTP ${response.code}")
            val body = response.body?.string() ?: "[]"
            val array = JSONArray(body)
            val result = ArrayList<Voice>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                result.add(
                    Voice(
                        name = o.optString("ShortName"),
                        locale = o.optString("Locale"),
                        gender = o.optString("Gender")
                    )
                )
            }
            if (result.isEmpty()) throw IOException("语音列表为空")
            return result
        }
    }

    private fun wsUrl(): String {
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        return "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$TRUSTED_TOKEN" +
            "&ConnectionId=$connectionId" +
            "&Sec-MS-GEC=${secMsGec()}" +
            "&Sec-MS-GEC-Version=${SEC_VERSION}"
    }

    private fun configMessage(): String {
        val payload = "{\"context\":{\"synthesis\":{\"audio\":{" +
            "\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"}," +
            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
        return "X-Timestamp:${timestamp()}\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            payload
    }

    private fun ssmlMessage(text: String, voice: Voice): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' " +
            "xml:lang='${voice.locale}'><voice name='${voice.name}'>" +
            "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>$escaped</prosody></voice></speak>"
        val requestId = UUID.randomUUID().toString().replace("-", "")
        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp()}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            ssml
    }

    private fun timestamp(): String {
        val format = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    /** 微软的防滥用签名：当前时间取整到 5 分钟后与固定 token 一起做 SHA-256 */
    private fun secMsGec(): String {
        var ticks = System.currentTimeMillis() / 1000.0 + 11_644_473_600.0
        ticks -= ticks % 300.0
        ticks *= 10_000_000.0
        val raw = String.format(Locale.US, "%.0f%s", ticks, TRUSTED_TOKEN)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format(Locale.US, "%02X", it) }
    }

    companion object {
        private const val TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val SEC_VERSION = "1-130.0.2849.68"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"

        /** 语音列表拉取失败时的兜底（乌兹别克语） */
        private val FALLBACK_VOICES = mapOf(
            "uz" to "uz-UZ-MadinaNeural"
        )
    }
}
