package app.roteiro.presidente.nucleo

import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Orientações para o presidente de um evento especial (S-125 discurso especial, S-126 Celebração).
 * O anúncio guarda os espaços a preencher entre colchetes, ex.: "[informe a hora e o local]".
 */
@Serializable
data class EventoEspecial(
    val tipo: String,                 // "especial" ou "celebracao"
    val idioma: String,
    val ano: Int? = null,
    val cantico: Int? = null,
    val canticoFinal: Int? = null,
    val tema: String = "",
    val anuncio: String = "",
    val notas: List<String> = emptyList(),   // instruções entre colchetes (fora do anúncio)
    val origem: String = "",
)

/** Lista de temas dos discursos públicos (S-99). */
data class ListaDiscursos(val idioma: String, val titulos: Map<String, String>)

/** Lê o texto (já extraído do PDF ou DOCX) das orientações S-99, S-125 e S-126. */
object LeitorEventos {

    private val RX_SIGLA = Regex("""\bS-(99|125|126)(?:-(\d{2}))?-([TS])\b""")
    private val RX_CANTICO = Regex("""\((?:número|numero|canción|cancion|cântico)\s+(\d{1,3})\)""", RegexOption.IGNORE_CASE)
    private val RX_TEMA = Regex("""(?m)^\s*(?:Tema|Título del discurso|Titulo del discurso|Título|Theme)\s*:\s*(.+)$""")
    private val RX_ASPAS = Regex("""^[\s“”"«»‘’']+|[\s“”"«»‘’'.]+$""")
    private val RX_ANO = Regex("""\b(20\d\d)\b""")

    private fun normal(s: String) = java.text.Normalizer.normalize(s.replace("\r", "").replace('\u00A0', ' ').replace('\u202F', ' '), java.text.Normalizer.Form.NFC)

    /** Sigla (S-99, S-125, S-126) e idioma ("pt"/"es") do documento, se reconhecidos. */
    fun sigla(texto: String): Pair<String, String>? {
        val m = RX_SIGLA.find(texto) ?: return null
        return "S-" + m.groupValues[1] to (if (m.groupValues[3] == "S") "es" else "pt")
    }

    private fun idiomaPorPalavras(t: String): String =
        if (Regex("""(?i)\b(canción|Conmemoración|Después|anuncio|presente al)\b""").containsMatchIn(t)) "es" else "pt"

    private fun ehTitulo(t: String) = Regex("""(?i)discurso especial|Celebração|Conmemoración|Ceia do Senhor|Cena del Señor""").containsMatchIn(t)

    /** Reconhece S-125/S-126 (ou textos parecidos). */
    fun lerEvento(texto: String, origem: String = ""): EventoEspecial? {
        val t = normal(texto)
        val sg = sigla(t)
        val tipo = when {
            sg?.first == "S-125" -> "especial"
            sg?.first == "S-126" -> "celebracao"
            Regex("""(?i)presidente d[oe]l? discurso especial""").containsMatchIn(t) -> "especial"
            Regex("""(?i)presidente d[ae] (la )?(Celebração|Conmemoración)""").containsMatchIn(t) -> "celebracao"
            else -> return null
        }
        if (!ehTitulo(t)) return null
        val lang = sg?.second ?: idiomaPorPalavras(t)
        val cants = RX_CANTICO.findAll(t).map { it.groupValues[1].toInt() }.toList()
        val tema = RX_TEMA.find(t)?.groupValues?.get(1)?.replace(RX_ASPAS, "")?.trim().orEmpty()

        // Instruções entre colchetes no começo de linha; a que vem antes do anúncio marca onde ele começa
        val linhas = t.lines()
        val blocos = ArrayList<Pair<Boolean, String>>()   // (é instrução, texto)
        var i = 0
        while (i < linhas.size) {
            val l = linhas[i].trim()
            if (l.startsWith("[")) {
                val sb = StringBuilder(l)
                while (!Regex("""\][:.]?\s*$""").containsMatchIn(sb) && i + 1 < linhas.size && !linhas[i + 1].trim().startsWith("[")) {
                    i++; sb.append(' ').append(linhas[i].trim())
                }
                blocos.add(true to sb.toString())
            } else if (l.isNotEmpty()) blocos.add(false to l)
            i++
        }
        val iAnuncio = blocos.indexOfFirst { it.first && Regex("""(?i)depois do discurso|después del discurso|leia o seguinte|lea el siguiente""").containsMatchIn(it.second) }
        var anuncio = ""
        if (iAnuncio >= 0) {
            val partes = ArrayList<String>()
            var j = iAnuncio + 1
            while (j < blocos.size && !blocos[j].first) { partes.add(blocos[j].second); j++ }
            anuncio = partes.filterNot { RX_SIGLA.containsMatchIn(it) }.joinToString(" ")
        }
        anuncio = limparAnuncio(anuncio)
        val notas = blocos.filterIndexed { k, b -> b.first && k != iAnuncio }
            .map { it.second.trim().removePrefix("[").replace(Regex("""\][:.]?\s*$"""), "").trim().trimEnd('.') + "." }
        val ano = Regex("""S-125-(\d{2})""").find(t)?.let { 2000 + it.groupValues[1].toInt() }
            ?: RX_ANO.find(t)?.groupValues?.get(1)?.toInt()
        return EventoEspecial(tipo, lang, ano, cants.getOrNull(0), cants.getOrNull(1), tema, anuncio, notas, origem)
    }

    /** Tira aspas externas, junta hifenização e transforma "[campo] ____" em "[campo]". */
    fun limparAnuncio(s: String): String {
        var a = s.replace(Regex("""\s+"""), " ").trim()
        a = a.replace(Regex("""\[([^\]]+)\]\s*_{2,}"""), "[$1]")
        a = a.replace(Regex("""\s*_{3,}"""), "")
        a = a.replace(Regex("""^[“"«]\s*"""), "")
        a = a.replace(Regex("""\s*[”"»]\s*([.:]?)\s*$"""), "$1")
        a = a.replace(Regex("""\s+([.,;:!?])"""), "$1")
        return a.trim()
    }

    /** Lista S-99: "1. Título" (o título pode continuar na linha seguinte). */
    fun lerDiscursos(textoBruto: String): ListaDiscursos? {
        val texto = normal(textoBruto)
        val sg = sigla(texto)
        if (sg?.first != "S-99" && !Regex("""(?i)Títulos d[oe]s? (los )?discursos públicos""").containsMatchIn(texto)) return null
        val lang = sg?.second ?: idiomaPorPalavras(texto)
        val out = LinkedHashMap<String, String>()
        var atual: Int? = null
        val sb = StringBuilder()
        fun fechar() { atual?.let { n -> out[n.toString()] = sb.toString().replace(Regex("""\s+"""), " ").trim() }; atual = null; sb.clear() }
        // "(Não use.)" fica na lista, para o número não ser escolhido por engano
        val rxNum = Regex("""^(\d{1,3})\.\s+(.+)$""")
        val ignorar = Regex("""(?i)^(S-99|Ano:|Año:|Títulos|Esta é|Esta es|para anotar|para anotar|\d+$)""")
        for (bruta in texto.lines()) {
            val l = bruta.trim()
            if (l.isEmpty()) continue
            val m = rxNum.find(l)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                if (n > (out.keys.lastOrNull()?.toInt() ?: 0) && n > (atual ?: 0)) { fechar(); atual = n; sb.append(m.groupValues[2]); continue }
            }
            if (ignorar.containsMatchIn(l)) { fechar(); continue }
            if (atual != null) { if (sb.endsWith("-") && !sb.endsWith(" -")) sb.append(l) else sb.append(' ').append(l) }
        }
        fechar()
        val limpo = out.filterValues { it.isNotBlank() }
        return if (limpo.size >= 20) ListaDiscursos(lang, limpo) else null
    }

    /** Texto de um .docx: um parágrafo por linha. */
    fun textoDocx(bytes: ByteArray): String? {
        var xml: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "word/document.xml") { xml = z.readBytes().toString(Charsets.UTF_8); break }
            }
        }
        val x = xml ?: return null
        val sb = StringBuilder()
        for (par in x.split("</w:p>")) {
            val linha = StringBuilder()
            Regex("""<w:t(?:\s[^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>""").findAll(par).forEach { m ->
                linha.append(if (m.value.startsWith("<w:t")) m.groupValues[1] else " ")
            }
            val l = linha.toString().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
            if (l.isNotBlank()) sb.append(l.trim()).append('\n')
        }
        return sb.toString()
    }
}
