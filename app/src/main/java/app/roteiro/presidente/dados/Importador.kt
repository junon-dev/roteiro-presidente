package app.roteiro.presidente.dados

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import app.roteiro.presidente.nucleo.BrochuraLida
import app.roteiro.presidente.nucleo.ItemTexto
import app.roteiro.presidente.nucleo.LeitorApostila
import app.roteiro.presidente.nucleo.LeitorBrochura
import app.roteiro.presidente.nucleo.PaginaPdf
import app.roteiro.presidente.nucleo.ResultadoLeitura
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Resultado da leitura de um arquivo escolhido pelo usuário. */
sealed interface Importado {
    data class Apostila(val r: ResultadoLeitura) : Importado
    data class Brochura(val b: BrochuraLida) : Importado
    data class Cancioneiro(val idioma: String, val titulos: Map<String, String>) : Importado
    data class CancioneiroErrado(val titulo: String, val simbolo: String) : Importado
    data class Falha(val motivo: String) : Importado
}

class Importador(private val ctx: Context) {

    private var pdfboxPronto = false

    fun nomeDe(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
    }

    /** Lê um arquivo: PDF (apostila ou brochura) ou .jwpub (cancioneiro). Rodar fora da thread principal. */
    fun ler(uri: Uri): Importado {
        val nome = nomeDe(uri)
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Importado.Falha("não foi possível abrir")
        return try {
            when {
                bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() -> lerPdf(bytes, nome)
                bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> lerJwpub(bytes, nome)
                else -> Importado.Falha("formato não reconhecido")
            }
        } catch (e: Throwable) {
            Importado.Falha((e.javaClass.simpleName + ": " + (e.message ?: "")).take(300))
        }
    }

    /* ---------- PDF ---------- */

    private class Coletor(val altura: Float, val dx: Float, val dy: Float) : PDFTextStripper() {
        val itens = ArrayList<ItemTexto>()
        override fun processTextPosition(text: TextPosition) {
            val s = text.unicode ?: return
            if (s.isBlank()) return
            val m = text.textMatrix
            val h = Math.abs(m.scaleY).let { if (it > 0f) it else Math.abs(m.scaleX) }
            // mesmas coordenadas que o pdf.js usa (a origem do PDFBox é o canto da área recortada)
            itens.add(ItemTexto(s, m.translateX + dx, altura - (m.translateY + dy), text.widthDirAdj, h))
        }
    }

    private fun paginas(doc: PDDocument): List<PaginaPdf> = (0 until doc.numberOfPages).map { i ->
        val caixa = doc.getPage(i).cropBox
        val c = Coletor(caixa.height, caixa.lowerLeftX, caixa.lowerLeftY)
        c.startPage = i + 1
        c.endPage = i + 1
        c.getText(doc)
        PaginaPdf(i + 1, caixa.width, caixa.height, c.itens)
    }

    private fun lerPdf(bytes: ByteArray, nome: String): Importado {
        if (!pdfboxPronto) { PDFBoxResourceLoader.init(ctx.applicationContext); pdfboxPronto = true }
        val pags = PDDocument.load(ByteArrayInputStream(bytes)).use { paginas(it) }
        LeitorBrochura.analisar(pags)?.let { return Importado.Brochura(it) }
        val r = LeitorApostila.analisar(pags, nome)
        return if (r.semanas.isEmpty()) Importado.Falha("nenhuma semana encontrada") else Importado.Apostila(r)
    }

    /* ---------- .jwpub (só o índice: números e títulos, que ficam abertos no arquivo) ---------- */

    private fun entradas(input: InputStream): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) out[e.name] = z.readBytes()
            }
        }
        return out
    }

    private val IDIOMA_MEPS = mapOf(0 to "en", 1 to "es", 5 to "pt")

    private fun lerJwpub(bytes: ByteArray, nome: String): Importado {
        val ext = entradas(ByteArrayInputStream(bytes))
        val manifesto = ext["manifest.json"]?.let { JSONObject(String(it, Charsets.UTF_8)) }
            ?: return Importado.Falha("arquivo .jwpub sem manifesto")
        val pub = manifesto.optJSONObject("publication") ?: return Importado.Falha("arquivo .jwpub sem publicação")
        val simbolo = pub.optString("symbol").ifEmpty { pub.optString("undatedSymbol") }
        val titulo = pub.optString("title")
        if (!simbolo.lowercase().startsWith("sjj")) return Importado.CancioneiroErrado(titulo, simbolo)
        val idioma = IDIOMA_MEPS[pub.optInt("language", -1)]
            ?: when {
                Regex("_T\\b|_T\\.").containsMatchIn(nome) -> "pt"
                Regex("_S\\b|_S\\.").containsMatchIn(nome) -> "es"
                else -> "pt"
            }
        val conteudo = ext["contents"] ?: return Importado.Falha("arquivo .jwpub sem conteúdo")
        val dbBytes = entradas(ByteArrayInputStream(conteudo)).entries.firstOrNull { it.key.endsWith(".db") }?.value
            ?: return Importado.Falha("banco de dados não encontrado")
        val tmp = File(ctx.cacheDir, "cancioneiro.db")
        tmp.writeBytes(dbBytes)
        val titulos = LinkedHashMap<String, String>()
        try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // O número vem em ChapterNumber; se faltar, tenta o início do título ("1. Título")
                val rxNum = Regex("^\\s*(\\d{1,3})\\s*[.)\\-–]?\\s+(.+)$")
                db.rawQuery("SELECT ChapterNumber, Title FROM Document ORDER BY DocumentId", null).use { c ->
                    while (c.moveToNext()) {
                        val bruto = (c.getString(1) ?: "").trim()
                        val m = rxNum.find(bruto)
                        val n = if (!c.isNull(0)) c.getInt(0) else m?.groupValues?.get(1)?.toIntOrNull() ?: continue
                        val t = (if (m != null && m.groupValues[1].toIntOrNull() == n) m.groupValues[2] else bruto).trim()
                        if (n > 0 && t.isNotEmpty() && !titulos.containsKey(n.toString())) titulos[n.toString()] = t
                    }
                }
            }
        } finally {
            tmp.delete()
        }
        return if (titulos.isEmpty()) Importado.Falha("nenhum título encontrado") else Importado.Cancioneiro(idioma, titulos)
    }
}
