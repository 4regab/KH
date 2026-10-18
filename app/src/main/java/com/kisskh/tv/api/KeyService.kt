package com.kisskh.tv.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Produces the `kkey` that the stream and subtitle endpoints require.
 *
 * The key comes from the site's obfuscated common.js. That file guards itself in ways a lightweight
 * JS engine chokes on, so we run it in a hidden WebView — the same V8 engine the browser uses — on a
 * page whose origin is kisskh.co. We fetch the live common.js (bundled copy as offline fallback) and
 * auto-detect the key function by its output shape, so the app keeps working if the site rotates the
 * cipher or renames the function. kkey depends only on fixed inputs, so results are deterministic.
 */
@SuppressLint("SetJavaScriptEnabled")
class KeyService(private val appContext: Context, private val http: OkHttpClient) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var webView: WebView? = null
    @Volatile private var ready = false

    private val appVer = "2.8.10"
    private val platformVer = "4830201"
    private val videoGuid = "62f176f3bb1b5b8e70e39932ad34a0c7"
    private val subGuid = "VgV52sWhwvBSf8BsM3BRY9weWiiCbtGp"

    suspend fun videoKey(episodeId: Int): String = key(episodeId, videoGuid)
    suspend fun subKey(episodeId: Int): String = key(episodeId, subGuid)

    private suspend fun key(episodeId: Int, guid: String): String {
        ensureReady()
        val args = buildString {
            append(episodeId); append(",null,")
            append(JSONObject.quote(appVer)); append(',')
            append(JSONObject.quote(guid)); append(',')
            append(platformVer); append(',')
            append((1..6).joinToString(",") { JSONObject.quote("kisskh") })
        }
        // Try every global function; keep the one returning a long hex string (the key function).
        val js = """
            (function(){
              var out="";
              for (var k in window){
                try {
                  var f=window[k];
                  if (typeof f==="function"){
                    var r=f($args);
                    if (typeof r==="string" && /^[0-9A-Fa-f]{100,}${'$'}/.test(r)){ out=r; break; }
                  }
                } catch(e){}
              }
              return out;
            })();
        """.trimIndent()
        return evaluate(js)
    }

    private suspend fun ensureReady() {
        if (ready) return
        val js = fetchCommonJs() ?: bundledCommonJs()
        suspendCancellableCoroutine { cont ->
            main.post {
                val wv = WebView(appContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        ready = true
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                webView = wv
                // base URL gives the page the kisskh.co origin the script expects.
                val html = "<!doctype html><html><head></head><body><script>$js</script></body></html>"
                wv.loadDataWithBaseURL("https://kisskh.co/", html, "text/html", "utf-8", null)
            }
        }
    }

    private suspend fun evaluate(js: String): String = suspendCancellableCoroutine { cont ->
        main.post {
            val wv = webView
            if (wv == null) { if (cont.isActive) cont.resume(""); return@post }
            wv.evaluateJavascript(js) { result ->
                // result is a JSON string literal, e.g. "\"ABC...\"" or "null"
                val s = try {
                    if (result == null || result == "null") "" else JSONObject("{\"v\":$result}").getString("v")
                } catch (_: Throwable) {
                    result?.trim('"') ?: ""
                }
                if (cont.isActive) cont.resume(s)
            }
        }
    }

    private fun fetchCommonJs(): String? = try {
        val req = Request.Builder().url("https://kisskh.co/common.js").header("Referer", "https://kisskh.co/").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string()
            if (r.isSuccessful && body != null && body.contains("fromCharCode")) {
                appContext.openFileOutput("common.js", Context.MODE_PRIVATE).use { it.write(body.toByteArray()) }
                body
            } else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun bundledCommonJs(): String =
        try {
            appContext.openFileInput("common.js").bufferedReader().readText()
        } catch (_: Throwable) {
            appContext.assets.open("common.js").bufferedReader().readText()
        }
}
