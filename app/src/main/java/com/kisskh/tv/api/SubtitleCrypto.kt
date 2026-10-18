package com.kisskh.tv.api

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * KissKH ships some subtitles AES-CBC encrypted, one base64 blob per cue line. The key/iv depend on
 * the file extension. Recovered from the site's scripts bundle (see API.md). Plain .srt/.vtt pass
 * through untouched.
 */
object SubtitleCrypto {
    private data class K(val key: String, val iv: String)

    private val byExt = mapOf(
        "txt" to K("8056483646328763", "6852612370185273"),
        "txt1" to K("AmSmZVcH93UQUezi", "ReBKWW8cqdjPEnF6"),
        // any other encrypted extension (e.g. .txt2) uses this pair
        "*" to K("sWODXX04QRTkHdlZ", "8pwhapJeC4hrS9hO"),
    )

    fun extensionOf(src: String): String =
        src.substringBefore('#').substringBefore('?').substringAfterLast('.').lowercase()

    fun isEncrypted(ext: String): Boolean = ext != "srt" && ext != "vtt"

    /** Decrypt one cue line; returns the input unchanged if it is not valid ciphertext. */
    fun decryptLine(line: String, ext: String): String {
        if (line.isBlank()) return line
        val k = byExt[ext] ?: byExt["*"]!!
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(k.key.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(k.iv.toByteArray(Charsets.UTF_8)),
            )
            String(cipher.doFinal(Base64.decode(line, Base64.DEFAULT)), Charsets.UTF_8)
        } catch (_: Throwable) {
            line
        }
    }
}
