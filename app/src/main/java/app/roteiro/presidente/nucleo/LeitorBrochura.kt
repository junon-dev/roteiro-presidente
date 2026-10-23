package app.roteiro.presidente.nucleo

import kotlinx.serialization.Serializable

/** Títulos oficiais de uma lição lidos da brochura (th ou lmd) importada pelo usuário. */
@Serializable
data class LicaoOficial(
    val titulo: String,
    val objetivo: String? = null,          // th: "O que você deve fazer" · lmd: "O que aprendemos" (ponto 2)
    val pontos: Map<String, String> = emptyMap(),   // th: "0","1","2"… · lmd: "3","4","5"
)

data class BrochuraLida(val pub: String, val idioma: String, val licoes: Map<Int, LicaoOficial>)

/**
 * Lê as brochuras "Melhore Sua Leitura e Seu Ensino" (th) e "Ame as Pessoas — Faça Discípulos" (lmd)
 * em PDF e extrai, de cada lição, o título, o objetivo e os títulos dos pontos.
 */
object LeitorBrochura {

    private val RX_SIMBOLO = Regex("\\((th|lmd)-([A-Z]{1,3})\\)")
    private val RX_NUM_GRANDE = Regex("^(\\d{1,2})\\s*(.*)$")
    private val RX_NUM_GRANDE_FIM = Regex("^(.*?)\\s*(\\d{1,2})$")
    private val RX_PONTO_LMD = Regex("^([2-5])\\.\\s+(.*)$")
    private val RX_ESPACOS = Regex("\\s+")
    private val RX_SUBITEM = Regex("^([a-z]\\.|\\d{1,2}\\.|˘)\\s")
    private val IDIOMA_SIMBOLO = mapOf("T" to "pt", "TPO" to "pt", "S" to "es")

    private fun juntar(linhas: List<String>): String =
        linhas.joinToString(" ").replace(Regex("(\\p{L})- (\\p{Ll})"), "$1$2").replace(RX_ESPACOS, " ").trim()

    /** Título do ponto: texto até o primeiro ponto final (". "). */
    private fun cabecalho(par: String): String {
        val i = Regex("\\.(\\s|$)").find(par)?.range?.first ?: return par.trim()
        return par.substring(0, i).trim()
    }

    /** Agrupa um parágrafo: a linha inicial e as continuações logo abaixo, com recuo e fonte iguais. */
    private fun paragrafo(segs: List<LeitorApostila.Segmento>, i: Int, recuoMin: Float, recuoMax: Float): String {
        val ini = segs[i]
        val partes = arrayListOf(ini.txt)
        var yAnt = ini.y
        for (k in i + 1 until segs.size) {
            val s = segs[k]
            if (Math.abs(s.h - ini.h) > 0.6f) continue
            if (s.y - yAnt > ini.h * 2.2f) break
            if (RX_SUBITEM.containsMatchIn(s.txt)) break
            val dx = s.x - ini.x
            if (dx < recuoMin || dx > recuoMax) { if (s.y > yAnt + 0.5f && Math.abs(dx) < 3f) break else continue }
            partes.add(s.txt); yAnt = s.y
        }
        return juntar(partes)
    }

    private class Pag(val segs: List<LeitorApostila.Segmento>)

    fun analisar(paginas: List<PaginaPdf>): BrochuraLida? {
        val pags = paginas.map { pg -> Pag(LeitorApostila.linhasDaPagina(pg).sortedWith(compareBy({ it.y }, { it.x }))) }
        val todo = pags.joinToString("\n") { p -> p.segs.joinToString("\n") { it.txt } }
        val sim = RX_SIMBOLO.find(todo) ?: return null
        val pub = sim.groupValues[1]
        val idioma = IDIOMA_SIMBOLO[sim.groupValues[2]] ?: LeitorApostila.detectarIdioma(todo).let { if (it == "es") "es" else "pt" }
        val licoes = LinkedHashMap<Int, LicaoOficial>()
        var atual: Int? = null
        for (p in pags) {
            val cab = numeroETitulo(p.segs, if (pub == "th") 20 else 12)
            if (cab != null) {
                if (licoes.containsKey(cab.first)) { atual = null; continue }
                atual = cab.first
                licoes[cab.first] = LicaoOficial(cab.second)
            }
            val n = atual ?: continue
            // a lição pode continuar na página seguinte (pontos 3 a 5 da lmd)
            val parcial = if (pub == "th") pontosTh(p.segs) else pontosLmd(p.segs)
            val l = licoes.getValue(n)
            licoes[n] = l.copy(objetivo = l.objetivo ?: parcial.objetivo, pontos = l.pontos + parcial.pontos.filterKeys { it !in l.pontos })
            if (pub == "th" && cab == null) atual = null
        }
        if (licoes.isEmpty()) return null
        return BrochuraLida(pub, idioma, licoes)
    }

    /** Número grande da lição + linhas do título (fonte grande, no alto da página). */
    private fun numeroETitulo(segs: List<LeitorApostila.Segmento>, max: Int): Pair<Int, String>? {
        // o número pode vir antes ("19 das pessoas") ou depois ("para entender o texto 4") do título
        var n = -1
        var resto = ""
        val grande = segs.firstOrNull { s ->
            if (s.h <= 45f) return@firstOrNull false
            val a = RX_NUM_GRANDE.find(s.txt)
            val b = RX_NUM_GRANDE_FIM.find(s.txt)
            when {
                a != null -> { n = a.groupValues[1].toInt(); resto = a.groupValues[2]; true }
                b != null -> { n = b.groupValues[2].toInt(); resto = b.groupValues[1]; true }
                else -> false
            }
        } ?: return null
        if (n < 1 || n > max) return null
        val linhasTit = segs.filter { it.h in 24f..45f && it.y < grande.y + 200 }.map { it.y to it.txt }.toMutableList()
        if (resto.isNotBlank()) linhasTit.add(grande.y to resto)
        val titulo = juntar(linhasTit.sortedBy { it.first }.map { it.second })
        return if (titulo.isBlank()) null else n to titulo
    }

    private fun pontosTh(segs: List<LeitorApostila.Segmento>): LicaoOficial {
        // Objetivo: primeiras linhas com fonte ~13 ("O QUE VOCÊ DEVE FAZER: …")
        val objLinhas = segs.filter { it.h in 12f..14f }
        var objetivo: String? = null
        if (objLinhas.isNotEmpty()) {
            val t = juntar(objLinhas.map { it.txt })
            objetivo = t.substringAfter(":", t).trim().ifEmpty { null }
        }
        // Pontos: linhas que começam com o marcador "˘" (fonte do corpo, coluna principal)
        val pontos = LinkedHashMap<String, String>()
        segs.forEachIndexed { i, s ->
            if (s.txt.startsWith("˘") && s.h in 9f..11.5f && s.x < 300) {
                val par = paragrafo(segs, i, 6f, 20f).removePrefix("˘").trim()
                pontos[pontos.size.toString()] = cabecalho(par)
            }
        }
        return LicaoOficial("", objetivo, pontos)
    }

    private fun pontosLmd(segs: List<LeitorApostila.Segmento>): LicaoOficial {
        val corpo = segs.filter { it.h in 9.5f..11.5f }
        val xCorpo = corpo.minOfOrNull { it.x } ?: return LicaoOficial("")   // os pontos começam na margem do texto
        val pontos = LinkedHashMap<String, String>()
        var aprendemos: String? = null
        segs.forEachIndexed { i, s ->
            if (Math.abs(s.x - xCorpo) > 3 || s.h !in 9.5f..11.5f) return@forEachIndexed
            val m = RX_PONTO_LMD.find(s.txt) ?: return@forEachIndexed
            val par = paragrafo(segs, i, -1f, 16f)
            val corpoPar = RX_PONTO_LMD.find(par)?.groupValues?.get(2) ?: return@forEachIndexed
            if (m.groupValues[1] == "2") aprendemos = corpoPar
            else pontos[m.groupValues[1]] = cabecalho(corpoPar)
        }
        return LicaoOficial("", aprendemos, pontos)
    }
}
