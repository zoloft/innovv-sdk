package com.zoloft.innovv.sdk.internal

/**
 * Tolerant extractor for the small XML dialect used by the camera.
 *
 * The camera emits simple, non-namespaced documents. The `3014` response is a
 * sequence of `<Function>` roots after the XML declaration, so a strict XML
 * parser cannot be used for every response. This reader deliberately extracts
 * known tags instead of building a full DOM.
 */
internal object Xml {
    fun blocks(text: String, tag: String): List<String> {
        val regex = Regex("(?s)<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    fun tag(text: String, tag: String): String? {
        val regex = Regex("(?s)<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)
    }

    fun textTag(text: String, tag: String): String? =
        tag(text, tag)?.let { decode(stripCdata(it)).trim() }.takeUnless { it.isNullOrEmpty() }

    fun intTag(text: String, tag: String): Int? = textTag(text, tag)?.toIntOrNull()

    fun longTag(text: String, tag: String): Long? = textTag(text, tag)?.toLongOrNull()

    fun doubleTag(text: String, tag: String): Double? = textTag(text, tag)?.toDoubleOrNull()

    fun stripCdata(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>") ->
                trimmed.removePrefix("<![CDATA[").removeSuffix("]]>")
            else -> trimmed
        }
    }

    fun decode(value: String): String {
        var result = value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { codePointToString(it) } ?: match.value
        }
        result = Regex("&#([0-9]+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull()?.let { codePointToString(it) } ?: match.value
        }
        return result.replace("&amp;", "&")
    }

    private fun codePointToString(codePoint: Int): String =
        if (codePoint <= 0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val value = codePoint - 0x10000
            val high = 0xD800 + (value shr 10)
            val low = 0xDC00 + (value and 0x3FF)
            high.toChar().toString() + low.toChar().toString()
        }
}
