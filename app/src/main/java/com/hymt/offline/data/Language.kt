package com.hymt.offline.data

import java.text.Collator
import java.util.Locale

/**
 * Hy-MT3 支持的 102 种语言（摘自官方模型卡语言表）。
 */
data class Lang(val code: String, val en: String, val zh: String) {

    /** 映射到系统语音识别 / TTS 可用的 BCP-47 标签 */
    val localeTag: String
        get() = when (code) {
            "zh-TR" -> "zh-TW"
            "yue" -> "zh-HK"
            "tl" -> "fil"
            "mn" -> "mn-Mong"
            "kk" -> "kk-Arab"
            else -> code
        }
}

object Languages {

    const val AUTO = "auto"

    val all: List<Lang> = listOf(
        Lang("zh", "Chinese", "中文"),
        Lang("en", "English", "英语"),
        Lang("ja", "Japanese", "日语"),
        Lang("ko", "Korean", "韩语"),
        Lang("fr", "French", "法语"),
        Lang("de", "German", "德语"),
        Lang("es", "Spanish", "西班牙语"),
        Lang("ru", "Russian", "俄语"),
        Lang("ar", "Arabic", "阿拉伯语"),
        Lang("pt", "Portuguese", "葡萄牙语"),
        Lang("it", "Italian", "意大利语"),
        Lang("th", "Thai", "泰语"),
        Lang("vi", "Vietnamese", "越南语"),
        Lang("id", "Indonesian", "印尼语"),
        Lang("hi", "Hindi", "印地语"),
        Lang("tr", "Turkish", "土耳其语"),
        Lang("zh-TR", "Traditional Chinese", "繁体中文"),
        Lang("yue", "Cantonese", "粤语"),
        Lang("nl", "Dutch", "荷兰语"),
        Lang("pl", "Polish", "波兰语"),
        Lang("uk", "Ukrainian", "乌克兰语"),
        Lang("cs", "Czech", "捷克语"),
        Lang("sv", "Swedish", "瑞典语"),
        Lang("da", "Danish", "丹麦语"),
        Lang("fi", "Finnish", "芬兰语"),
        Lang("nb", "Norwegian", "挪威语"),
        Lang("el", "Greek", "希腊语"),
        Lang("he", "Hebrew", "希伯来语"),
        Lang("fa", "Persian", "波斯语"),
        Lang("ur", "Urdu", "乌尔都语"),
        Lang("bn", "Bengali", "孟加拉语"),
        Lang("ta", "Tamil", "泰米尔语"),
        Lang("te", "Telugu", "泰卢固语"),
        Lang("mr", "Marathi", "马拉地语"),
        Lang("gu", "Gujarati", "古吉拉特语"),
        Lang("kn", "Kannada", "卡纳达语"),
        Lang("ml", "Malayalam", "马拉雅拉姆语"),
        Lang("pa", "Punjabi", "旁遮普语"),
        Lang("si", "Sinhala", "僧伽罗语"),
        Lang("km", "Khmer", "高棉语"),
        Lang("my", "Burmese", "缅甸语"),
        Lang("lo", "Lao", "老挝语"),
        Lang("ms", "Malay", "马来语"),
        Lang("tl", "Filipino", "菲律宾语"),
        Lang("jv", "Javanese", "爪哇语"),
        Lang("su", "Sundanese", "巽他语"),
        Lang("ceb", "Cebuano", "宿务语"),
        Lang("war", "Waray", "瓦瑞语"),
        Lang("sw", "Swahili", "斯瓦希里语"),
        Lang("am", "Amharic", "阿姆哈拉语"),
        Lang("so", "Somali", "索马里语"),
        Lang("mg", "Malagasy", "马达加斯加语"),
        Lang("ht", "Haitian Creole", "海地克里奥尔语"),
        Lang("af", "Afrikaans", "南非荷兰语"),
        Lang("sq", "Albanian", "阿尔巴尼亚语"),
        Lang("hy", "Armenian", "亚美尼亚语"),
        Lang("az", "Azerbaijani", "阿塞拜疆语"),
        Lang("eu", "Basque", "巴斯克语"),
        Lang("be", "Belarusian", "白俄罗斯语"),
        Lang("bg", "Bulgarian", "保加利亚语"),
        Lang("ca", "Catalan", "加泰罗尼亚语"),
        Lang("hr", "Croatian", "克罗地亚语"),
        Lang("cy", "Welsh", "威尔士语"),
        Lang("et", "Estonian", "爱沙尼亚语"),
        Lang("eo", "Esperanto", "世界语"),
        Lang("gl", "Galician", "加利西亚语"),
        Lang("ka", "Georgian", "格鲁吉亚语"),
        Lang("hu", "Hungarian", "匈牙利语"),
        Lang("is", "Icelandic", "冰岛语"),
        Lang("ga", "Irish", "爱尔兰语"),
        Lang("gd", "Scottish Gaelic", "苏格兰盖尔语"),
        Lang("kk", "Arabic Kazakh", "阿拉伯字母哈萨克语"),
        Lang("kk-Cyrl", "Cyrillic Kazakh", "西里尔哈萨克语"),
        Lang("ky", "Kyrgyz", "吉尔吉斯语"),
        Lang("ku", "Kurdish", "库尔德语"),
        Lang("ckb", "Central Kurdish", "中库尔德语"),
        Lang("lv", "Latvian", "拉脱维亚语"),
        Lang("lt", "Lithuanian", "立陶宛语"),
        Lang("lb", "Luxembourgish", "卢森堡语"),
        Lang("mk", "Macedonian", "马其顿语"),
        Lang("mt", "Maltese", "马耳他语"),
        Lang("mn", "Traditional Mongolian", "传统蒙古语"),
        Lang("mn-Cyrl", "Cyrillic Mongolian", "西里尔蒙古语"),
        Lang("ne", "Nepali", "尼泊尔语"),
        Lang("or", "Odia", "奥里亚语"),
        Lang("ps", "Pashto", "普什图语"),
        Lang("ro", "Romanian", "罗马尼亚语"),
        Lang("sa", "Sanskrit", "梵语"),
        Lang("sr", "Serbian", "塞尔维亚语"),
        Lang("sd", "Sindhi", "信德语"),
        Lang("sk", "Slovak", "斯洛伐克语"),
        Lang("sl", "Slovenian", "斯洛文尼亚语"),
        Lang("as", "Assamese", "阿萨姆语"),
        Lang("bo", "Tibetan", "藏语"),
        Lang("tg", "Tajik", "塔吉克语"),
        Lang("tt", "Tatar", "鞑靼语"),
        Lang("tk", "Turkmen", "土库曼语"),
        Lang("ug", "Uyghur", "维吾尔语"),
        Lang("uz", "Uzbek", "乌兹别克语"),
        Lang("yi", "Yiddish", "意第绪语"),
        Lang("ba", "Bashkir", "巴什基尔语"),
        Lang("nn", "Norwegian Nynorsk", "新挪威语")
    )

    private val byCode: Map<String, Lang> = all.associateBy { it.code }

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    val popularCodes: List<String> = listOf(
        "zh", "en", "ja", "ko", "fr", "de", "es", "ru",
        "ar", "pt", "it", "th", "vi", "id", "hi", "tr"
    )

    val popular: List<Lang> = popularCodes.mapNotNull { byCode[it] }

    val others: List<Lang> = all
        .filter { it.code !in popularCodes }
        .sortedWith { a, b -> collator.compare(a.zh, b.zh) }

    fun of(code: String): Lang? = byCode[code]

    fun displayName(code: String): String =
        if (code == AUTO) "自动检测" else byCode[code]?.zh ?: code
}
