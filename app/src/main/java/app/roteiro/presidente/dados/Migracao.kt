package app.roteiro.presidente.dados

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.Semana
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Recupera os dados da versão 1 do app (HTML dentro de uma WebView), que ficavam no
 * armazenamento local do endereço https://appassets.androidplatform.net.
 * Abre uma página mínima nesse mesmo endereço, lê as chaves "rp.*" e devolve um [Arquivo].
 */
class Migracao(private val ctx: Context, private val json: Json) {

    /** Há dados da versão anterior? (a WebView cria a pasta app_webview) */
    fun temVersaoAnterior(): Boolean = File(ctx.applicationInfo.dataDir, "app_webview").exists()

    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun executar(aoTerminar: (Arquivo?) -> Unit) {
        val host = "appassets.androidplatform.net"
        val pagina = "<!doctype html><html><body><script>" +
            "var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k.indexOf('rp.')===0)o[k]=localStorage.getItem(k);}" +
            "Migrar.pronto(JSON.stringify(o));</script></body></html>"
        val loader = WebViewAssetLoader.Builder().setDomain(host)
            .addPathHandler("/migrar/", WebViewAssetLoader.PathHandler { _ -> WebResourceResponse("text/html", "utf-8", pagina.byteInputStream()) })
            .build()
        val w = WebView(ctx)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        var terminou = false
        fun fim(a: Arquivo?) {
            if (terminou) return
            terminou = true
            w.post { aoTerminar(a); w.destroy(); web = null }
        }
        w.addJavascriptInterface(object {
            @JavascriptInterface
            fun pronto(dados: String) { fim(converter(dados)) }
        }, "Migrar")
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        w.loadUrl("https://$host/migrar/index.html")
        w.postDelayed({ fim(null) }, 8000)
    }

    private fun converter(dados: String): Arquivo? = try {
        val m = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), dados)
        if (m.isEmpty()) null else {
            val semanas = m.filterKeys { it.startsWith("rp.sem.") }.mapNotNull { (k, v) ->
                runCatching { k.removePrefix("rp.sem.") to json.decodeFromString(EstadoSemanaDto.serializer(), v) }.getOrNull()
            }.toMap()
            Arquivo(
                cfg = m["rp.cfg"]?.let { runCatching { json.decodeFromString(Config.serializer(), it) }.getOrNull() } ?: Config(),
                importadas = m["rp.importadas"]?.let { runCatching { json.decodeFromString(ListSerializer(Semana.serializer()), it) }.getOrNull() } ?: emptyList(),
                removidas = m["rp.removidas"]?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() } ?: emptyList(),
                semanas = semanas,
            )
        }
    } catch (e: Exception) { null }
}
