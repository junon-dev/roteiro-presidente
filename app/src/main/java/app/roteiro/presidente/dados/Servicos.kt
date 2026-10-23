package app.roteiro.presidente.dados

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import app.roteiro.presidente.nucleo.Bloco
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.ItemRoteiro
import app.roteiro.presidente.nucleo.RoteiroMontado
import app.roteiro.presidente.nucleo.Seg
import app.roteiro.presidente.nucleo.Semana
import app.roteiro.presidente.nucleo.Textos

/** Aviso de fim de tempo: vibração e bipe curto. */
object Avisos {
    fun vibrar(ctx: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (v == null || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
    }

    fun bipe() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 900)
        } catch (_: RuntimeException) { }
    }
}

/** Impressão do roteiro (ou "Salvar como PDF") pelo serviço de impressão do Android. */
object Impressao {
    private var webAtiva: WebView? = null   // mantém a WebView viva até o fim da impressão

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    // textos com <b>/<i> vindos do roteiro: escapa tudo e devolve só essas marcas
    private fun marc(s: String) = esc(s).replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>")
        .replace("&lt;i&gt;", "<i>").replace("&lt;/i&gt;", "</i>")

    fun html(sem: Semana, e: EstadoSemana, r: RoteiroMontado, tituloSemana: String): String {
        val s = Textos.de(sem.idioma)
        val sb = StringBuilder()
        sb.append("""<!doctype html><html><head><meta charset="utf-8"><style>
            body{font-family:sans-serif;font-size:11pt;color:#1d2430;margin:0}
            h1{font-size:16pt;margin:0 0 4pt} .sup{font-size:9pt;color:#5b6472;text-transform:uppercase;letter-spacing:.05em}
            .sec{color:#fff;font-weight:bold;font-size:9pt;text-transform:uppercase;padding:3pt 8pt;margin:10pt 0 4pt;border-radius:3pt}
            .tesouros{background:#2f6f7a}.ministerio{background:#a8740f}.vida{background:#a33a2a}
            .b{display:flex;gap:8pt;border-bottom:1px solid #ddd;padding:5pt 0;page-break-inside:avoid}
            .h{width:38pt;font-weight:bold} .c{flex:1} .t{font-weight:bold}
            .f{background:#eef4fb;border-left:3px solid #3b6fb6;padding:3pt 6pt;margin:3pt 0}
            .i{color:#5b6472;font-size:9.5pt;margin:2pt 0} .n{font-weight:bold;border-bottom:1px solid #888;padding:0 2pt}
            .cons{background:#fbf6ea;border:1px solid #ead8ad;padding:4pt 6pt;margin-top:4pt;font-size:9.5pt}
            .dica{font-size:9.5pt;color:#5b6472;font-style:italic;margin:2pt 0 2pt 46pt}
            ul{margin:2pt 0;padding-left:14pt}
            </style></head><body>""")
        sb.append("<div class='sup'>${esc(s["meeting"])} · ${esc(sem.leitura)}</div><h1>${esc(tituloSemana)}</h1>")
        fun nome(k: String, rot: String): String {
            val v = e.valor(k)
            return if (v.isNotBlank()) "<span class='n'>${esc(v)}</span>" else "<span class='n'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>"
        }
        fun bloco(b: Bloco) {
            sb.append("<div class='b'><div class='h'>${b.hora}</div><div class='c'><div class='t'>${esc(b.titulo)}")
            b.subtitulo?.let { sb.append(" — ${esc(it)}") }
            if (b.durTxt.isNotEmpty()) sb.append(" <span class='i'>(${esc(b.durTxt)})</span>")
            sb.append("</div>")
            val falas = b.falas.joinToString("") { f -> "<div class='f'>" + f.joinToString("") { seg -> if (seg is Seg.Txt) esc(seg.s) else (seg as Seg.Nome).let { nome(it.k, it.rotulo) } } + "</div>" }
            if (!b.falaNoFim) sb.append(falas)
            b.instr.forEach { sb.append("<div class='i'>${marc(it)}</div>") }
            if (b.lista.isNotEmpty()) sb.append("<ul>" + b.lista.joinToString("") { "<li>${marc(it)}</li>" } + "</ul>")
            b.campos.filter { c -> b.falas.flatten().none { it is Seg.Nome && it.k == c.k } }.forEach { sb.append("<div class='i'>${esc(it.rotulo)}: ${nome(it.k, it.rotulo)}</div>") }
            if (b.salaB.isNotEmpty()) sb.append("<div class='i'>${esc(s["sala_b"])}: " + b.salaB.joinToString(" / ") { nome(it.k, it.rotulo) } + "</div>")
            b.areas.forEach { a -> e.valor(a.k).takeIf { it.isNotBlank() }?.let { sb.append("<div class='i'><b>${esc(a.rotulo)}:</b> ${esc(it)}</div>") } }
            if (b.falaNoFim) sb.append(falas)
            b.conselho?.let { c ->
                sb.append("<div class='cons'><b>${esc(s["cons_t"])}</b> · ${esc(c.licao ?: "")}${c.licaoTitulo?.let { " — " + esc(it) } ?: ""}<ul>")
                c.pontos.forEach { p -> sb.append("<li>${p.titulo?.let { "<b>${esc(it)}</b> " } ?: ""}${esc(p.texto)}</li>") }
                sb.append("</ul></div>")
            }
            b.termino?.let { sb.append("<div class='i' style='text-align:right'>${esc(s["termino"])}: <b>$it</b></div>") }
            sb.append("</div></div>")
        }
        for (it in r.itens) when (it) {
            is ItemRoteiro.Secao -> sb.append("<div class='sec ${it.sec}'>${esc(it.titulo)}</div>")
            is ItemRoteiro.Dica -> sb.append("<div class='dica'>${esc(it.texto)}</div>")
            is ItemRoteiro.BlocoR -> bloco(it.b)
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun imprimir(ctx: Context, nomeTrabalho: String, html: String) {
        val w = WebView(ctx)
        webAtiva = w
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print(nomeTrabalho, view.createPrintDocumentAdapter(nomeTrabalho),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        w.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
