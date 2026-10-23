package app.roteiro.presidente.nucleo

import java.text.Normalizer
import java.time.LocalDate

/** Um pedaço de texto posicionado na página (y = distância do topo até a linha de base). */
data class ItemTexto(var s: String, val x: Float, val y: Float, val w: Float, val h: Float)

data class PaginaPdf(val n: Int, val largura: Float, val altura: Float, val itens: List<ItemTexto>)

data class ResultadoLeitura(val idioma: String, val ano: Int, val semanas: List<Semana>)

/**
 * Lê a apostila "Nossa Vida e Ministério Cristão" / "Vida y Ministerio Cristianos" a partir do
 * texto posicionado das páginas e devolve as semanas com as partes, tempos e lições.
 * (Mesma lógica da versão HTML, que foi validada com as apostilas em PT e ES.)
 */
object LeitorApostila {

    private val ACENTOS = mapOf(
        "ˆ" to '̂', "˜" to '̃', "´" to '́', "`" to '̀', "¨" to '̈',
        "¸" to '̧', "˚" to '̊', "^" to '̂', "~" to '̃',
    )
    private val COMPAT = mapOf(
        '́' to "aeiouıAEIOUy", '̀' to "aeiouıAEIOU", '̂' to "aeiouAEIOU",
        '̈' to "aeiouıAEIOU", '̃' to "aonAON", '̧' to "cC", '̊' to "aA",
    )

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    /* ---------- 1. Itens -> linhas -> segmentos ---------- */

    /** Junta acentos soltos (ˆ ´ ˜ ¸) à letra correspondente. */
    fun comporAcentos(itensOrig: List<ItemTexto>): List<ItemTexto> {
        val itens = itensOrig.map { it.copy() }
        val base = ArrayList<ItemTexto>()
        val acentos = ArrayList<ItemTexto>()
        for (it in itens) {
            val t = it.s.trim()
            if (t.length == 1 && ACENTOS.containsKey(t)) acentos.add(it) else base.add(it)
        }
        for (a in acentos) {
            val comb = ACENTOS.getValue(a.s.trim())
            val compat = COMPAT.getValue(comb)
            val cx = a.x + a.w / 2
            var melhor: ItemTexto? = null
            var idxM = -1
            var dist = 1e9f
            for (b in base) {
                if (Math.abs(b.y - a.y) > b.h * 0.9f) continue
                if (comb != '̧' && a.y > b.y + 0.5f) continue   // acento fica acima da letra
                if (comb == '̧' && a.y < b.y - 2f) continue     // cedilha fica embaixo
                if (b.x > cx + 8 || b.x + b.w < cx - 8) continue
                val l = b.s.length
                val cw = b.w / maxOf(1, l)
                for (k in 0 until l) {
                    if (compat.indexOf(b.s[k]) < 0) continue
                    val d = Math.abs(b.x + (k + 0.5f) * cw - cx) + Math.abs(b.y - a.y) * 0.3f
                    if (d < dist) { dist = d; melhor = b; idxM = k }
                }
            }
            val m = melhor ?: continue
            if (dist > 6f) continue
            var ch = m.s[idxM]
            if (ch == 'ı') ch = 'i'
            m.s = m.s.substring(0, idxM) + nfc("$ch$comb") + m.s.substring(idxM + 1)
        }
        for (b in base) b.s = b.s.replace('ı', 'i')
        return base
    }

    class Segmento(val y: Float, val x: Float, var h: Float) {
        val partes = StringBuilder()
        var txt = ""
    }

    private val RX_INVISIVEIS = Regex("[\\uE000-\\uF8FF\\u0000-\\u001F]")
    private val RX_ESPACOS_UNI = Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000]")
    private val RX_ESPACOS = Regex("\\s+")

    fun linhasDaPagina(pg: PaginaPdf): List<Segmento> {
        val itens = comporAcentos(pg.itens).sortedWith(compareBy<ItemTexto> { it.y }.thenBy { it.x })
        class Linha(val y: Float) { val itens = ArrayList<ItemTexto>() }
        val linhas = ArrayList<Linha>()
        for (it in itens) {
            var l = linhas.firstOrNull { L -> Math.abs(L.y - it.y) <= maxOf(2f, it.h * 0.3f) }
            if (l == null) { l = Linha(it.y); linhas.add(l) }
            l.itens.add(it)
        }
        val segs = ArrayList<Segmento>()
        for (l in linhas) {
            l.itens.sortBy { it.x }
            var seg: Segmento? = null
            var fim = -1e9f
            for (it in l.itens) {
                val gap = it.x - fim
                if (seg == null || gap > maxOf(14f, it.h * 1.6f)) {
                    seg = Segmento(l.y, it.x, it.h)
                    segs.add(seg)
                } else if (gap > it.h * 0.15f) seg.partes.append(' ')
                seg.partes.append(it.s)
                seg.h = maxOf(seg.h, it.h)
                fim = it.x + it.w
            }
        }
        for (s in segs) {
            s.txt = s.partes.toString()
                .replace(RX_ESPACOS_UNI, " ")
                .replace(RX_INVISIVEIS, "")
                .replace(RX_ESPACOS, " ").trim()
        }
        return segs
    }

    class TextoPagina(val cab: List<Segmento>, val linhas: List<Segmento>)

    /** Ordem de leitura: cabeçalho, coluna esquerda, coluna direita. */
    fun textoDaPagina(pg: PaginaPdf): TextoPagina {
        val segs = linhasDaPagina(pg).filter { it.h >= 7.6f && it.y < pg.altura - 30 }
        val meio = pg.largura * 0.47f
        val ord = compareBy<Segmento> { it.y }.thenBy { it.x }
        val cab = segs.filter { it.y < 22 }
        val esq = segs.filter { it.y >= 22 && it.x < meio }.sortedWith(ord)
        val dir = segs.filter { it.y >= 22 && it.x >= meio }.sortedWith(ord)
        return TextoPagina(cab, esq + dir)
    }

    /* ---------- 2. Texto -> semanas ---------- */

    private val MESES = mapOf(
        "JANEIRO" to 1, "FEVEREIRO" to 2, "MARÇO" to 3, "MARCO" to 3, "ABRIL" to 4, "MAIO" to 5, "JUNHO" to 6,
        "JULHO" to 7, "AGOSTO" to 8, "SETEMBRO" to 9, "OUTUBRO" to 10, "NOVEMBRO" to 11, "DEZEMBRO" to 12,
        "ENERO" to 1, "FEBRERO" to 2, "MARZO" to 3, "MAYO" to 5, "JUNIO" to 6, "JULIO" to 7,
        "SEPTIEMBRE" to 9, "SETIEMBRE" to 9, "OCTUBRE" to 10, "NOVIEMBRE" to 11, "DICIEMBRE" to 12,
    )
    val NOMES_MES = mapOf(
        "pt" to listOf("", "janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"),
        "es" to listOf("", "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
    )
    private val RX_MES = MESES.keys.sortedByDescending { it.length }.joinToString("|")
    private val RX_CAB = Regex("^(\\d{1,2})(?:DE($RX_MES))?(?:-|–|—|A)(\\d{1,2})\\D{0,3}?DE($RX_MES)(.*)$")
    private val RX_ANO = Regex("(?:$RX_MES)(?:-|–|Y|E)(?:$RX_MES)DE(20\\d\\d)")
    private val RX_INI_CAB = Regex("\\d{1,2}(?:DE|-|–|—|A)")

    private fun compacto(s: String) = nfc(s).uppercase().replace(RX_ESPACOS, "")

    fun tituloLivro(restoOrig: String): String {
        // "JEREMIAS45-46" -> "Jeremias 45-46" ; "JEREMÍAS49,50" -> "Jeremías 49, 50"
        val resto = (restoOrig.split('|').map { it.trim() }.firstOrNull { Regex("\\p{Lu}").containsMatchIn(it) } ?: "")
            .replace(Regex("^[^0-9\\p{Lu}]+"), "")
        val m = Regex("^(\\d?)(\\p{Lu}+)(\\d.*)?$").find(resto) ?: return resto
        val livroMai = m.groupValues[2]
        val livro = livroMai.substring(0, 1) + livroMai.substring(1).lowercase()
        val caps = m.groupValues[3].replace(Regex(",(?=\\d)"), ", ")
        return ((if (m.groupValues[1].isNotEmpty()) m.groupValues[1] + " " else "") + livro + " " + caps).trim()
    }

    fun detectarIdioma(txt: String): String {
        val t = txt.uppercase()
        val pt = Regex("CÂNTICO|ORAÇÃO|LIÇÃO|COMENTÁRIOS").findAll(t).count()
        val es = Regex("CANCIÓN|ORACIÓN|LECCIÓN|PALABRAS DE").findAll(t).count()
        return if (es > pt) "es" else "pt"
    }

    private fun rx(p: String) = Regex("(?iu)$p")

    private val TIT_MINISTERIO = rx("^(Iniciando conversas|Cultivando o interesse|Fazendo disc[ií]pulos|Explicando suas cren[çc]as|Discurso|O que voc[êe] diria\\?|Empiece conversaciones|Empecemos conversaciones|Haga revisitas|Hagamos revisitas|Haga disc[ií]pulos|Hagamos disc[ií]pulos|Explique sus creencias|Expliquemos (?:nuestras|sus) creencias|¿Qué (?:diría usted|dirías|diría)\\?)")
    private val TIT_SEM_ESTUDANTE = rx("^(O que voc[êe] diria\\?|¿Qué (?:diría usted|dirías|diría)\\?)")
    private val RX_CANT_ORACAO = rx("(?:C[âa]ntico|Canci[óo]n)\\s+(\\d+)\\s+(?:e oração|e ora[çc][ãa]o|y oraci[óo]n)")
    private val RX_CANT_SO = rx("^(?:C[âa]ntico|Canci[óo]n)\\s+(\\d+)\\s*$")
    private val RX_CANT_INI = rx("^(?:C[âa]ntico|Canci[óo]n)\\s+\\d+")
    private val RX_MIN = rx("\\((\\d+)\\s*mins?\\.?\\)")
    private val RX_NUM_PARTE = Regex("^(\\d{1,2})\\.\\s*(\\S.*)$")
    private val PARADAS_MAI = Regex("^(TESOUROS|TESOROS|FAÇA SEU|SEAMOS|NOSSA$|NUESTRA$|NOSSA VIDA|NUESTRA VIDA|VIDA CRIST|MEJORES MAESTROS|NO MINIST|DE LA BIBLIA$|PALAVRA DE DEUS)")
    private val PARADAS_MIN = rx("^(Coment[áa]rios finais|Palabras de conclusi[óo]n|Que joias|¿Qué perlas)")
    private val RX_HIFEN = Regex("(\\p{L})- (\\p{Ll})")
    private val RX_LICAO = rx("\\b(th|lmd)\\s+(?:lição|lección|lecci[óo]n)\\s+(\\d+)(?:\\s+(?:ponto|punto)\\s+(\\d+))?")
    private val RX_APA = rx("lmd\\s+ap[êe]ndice\\s+A\\s+(?:ponto|punto)\\s+(\\d+)")
    private val RX_CONSID = rx("Considera[çc][ãa]o|An[áa]lisis con el auditorio")
    private val RX_PASSAGEM = Regex("^([^(]+?)\\s*\\(")
    private val RX_DISCURSO = rx("^Discurso")
    private val RX_EXPLIC = rx("Explicando|Explique|Expliquemos")
    private val RX_DISC_DET = rx("Discurso")
    private val RX_DEMONSTRA = rx("Demonstra")
    private val RX_EBC = rx("Estudo b[íi]blico de congrega|Estudio b[íi]blico de la congregaci")
    private val RX_MATERIA = Regex("^[^.]*?\\b(\\w+ cap\\.\\s*\\d+)")
    private val RX_NECESS = rx("Necessidades locais|Necesidades de la congregaci")
    private val RX_CENA = Regex("\\b(DE CASA EM CASA|TESTEMUNHO INFORMAL|TESTEMUNHO PÚBLICO|DE CASA EN CASA|PREDICACIÓN INFORMAL|PREDICACIÓN PÚBLICA)\\.?")
    private val RX_REF_LICAO = rx("\\((?:lmd|th)\\s[^)]*\\)\\.?")
    private val RX_PREFIXO_CONSID = rx("^\\s*(Considera[çc][ãa]o|An[áa]lisis con el auditorio[^.]*)\\.\\s*")
    private val RX_ESPACO_PONT = Regex("\\s+([.,;])")

    private class Cabecalho(val i: Int, val fimCab: Int, val n: Int, val titulo: String, val min: Int, val resto: String)

    fun analisarSemana(linhas: List<String>): MutableList<Parte> {
        val inicios = ArrayList<Cabecalho>()
        for (i in linhas.indices) {
            val m = RX_NUM_PARTE.find(linhas[i]) ?: continue
            var tit = m.groupValues[2]
            var j = i
            var min: Int? = null
            for (k in 0 until 3) {
                if (j >= linhas.size) break
                val mm = RX_MIN.find(if (k == 0) tit else linhas[j])
                if (mm != null) { min = mm.groupValues[1].toInt(); break }
                j++
                if (j < linhas.size) tit += "\n" + linhas[j]
            }
            if (min == null) continue
            val corte = RX_MIN.find(tit)!!.range.first
            val titulo = tit.substring(0, corte).replace("-\n", "").replace("\n", " ").trim()
            val resto = tit.substring(corte).replaceFirst(RX_MIN, "").trim()
            inicios.add(Cabecalho(i, j, m.groupValues[1].toInt(), titulo, min, resto))
        }
        val vistos = HashSet<Int>()
        val cabs = inicios.filter { vistos.add(it.n) }.sortedBy { it.n }

        val partes = ArrayList<Parte>()
        for (c in cabs) {
            val prox = inicios.filter { it.i > c.fimCab }.map { it.i }
            val limite = prox.minOrNull() ?: linhas.size
            val det = ArrayList<String>()
            if (c.resto.isNotEmpty()) det.add(c.resto)
            for (k in c.fimCab + 1 until limite) {
                val l = linhas[k]
                if (PARADAS_MAI.containsMatchIn(l) || PARADAS_MIN.containsMatchIn(l) || RX_CANT_INI.containsMatchIn(l)) break
                det.add(l)
            }
            val detalhe = det.joinToString(" ").replace(RX_HIFEN, "$1$2").replace(RX_ESPACOS, " ").trim()
            val lic = RX_LICAO.find(detalhe)?.let {
                Licao(it.groupValues[1].lowercase(), it.groupValues[2].toInt(), it.groupValues[3].toIntOrNull(), it.value)
            }
            partes.add(Parte(
                n = c.n, titulo = c.titulo, min = c.min, detalhe = detalhe, licao = lic,
                apendiceA = RX_APA.find(detalhe)?.groupValues?.get(1)?.toInt(),
                consideracao = RX_CONSID.containsMatchIn(detalhe),
            ))
        }

        // Classificação por seção e tipo
        var secao = "tesouros"
        for (idx in partes.indices) {
            var p = partes[idx]
            var tipo: String
            var passagem: String? = null
            var materia: String? = null
            when (p.n) {
                1 -> tipo = "discurso"
                2 -> tipo = "joias"
                3 -> { tipo = "leitura"; passagem = RX_PASSAGEM.find(p.detalhe)?.groupValues?.get(1)?.trim() ?: p.detalhe }
                else -> {
                    secao = if (secao != "vida" && TIT_MINISTERIO.containsMatchIn(p.titulo)) "ministerio" else "vida"
                    if (secao == "ministerio") {
                        tipo = when {
                            TIT_SEM_ESTUDANTE.containsMatchIn(p.titulo) -> "consideracao_min"
                            RX_DISCURSO.containsMatchIn(p.titulo) -> "discurso_estudante"
                            RX_EXPLIC.containsMatchIn(p.titulo) && RX_DISC_DET.containsMatchIn(p.detalhe) && !RX_DEMONSTRA.containsMatchIn(p.detalhe) -> "discurso_estudante"
                            else -> "estudante"
                        }
                    } else {
                        tipo = when {
                            RX_EBC.containsMatchIn(p.titulo) -> { materia = RX_MATERIA.find(p.detalhe)?.groupValues?.get(1) ?: p.detalhe; "ebc" }
                            RX_NECESS.containsMatchIn(p.titulo) -> "necessidades"
                            else -> "consideracao"
                        }
                    }
                }
            }
            var detalhe = p.detalhe
            if (tipo == "leitura") detalhe = (passagem ?: "") + (p.licao?.let { " (${it.txt})" } ?: "")
            val cena = RX_CENA.find(detalhe)
            var texto = if (cena != null) detalhe.replaceFirst(cena.value, "") else detalhe
            texto = texto.replace(RX_REF_LICAO, "").replaceFirst(RX_PREFIXO_CONSID, "")
                .replace(RX_ESPACO_PONT, "$1").replace(RX_ESPACOS, " ").trim()
            val sec = if (p.n <= 3) "tesouros" else if (tipo in setOf("estudante", "discurso_estudante", "consideracao_min")) "ministerio" else "vida"
            p = p.copy(tipo = tipo, sec = sec, detalhe = detalhe, passagem = passagem, materia = materia,
                cena = cena?.groupValues?.get(1), texto = texto)
            partes[idx] = p
        }
        return partes
    }

    private fun cantico(rx: Regex, txt: String) = rx.findAll(txt).map { it.groupValues[1].toInt() }.toList()

    /** Lê as páginas (texto posicionado) e devolve as semanas encontradas. */
    fun analisar(paginas: List<PaginaPdf>, nomeArquivo: String = "", anoAtual: Int = LocalDate.now().year): ResultadoLeitura {
        class Bloco(val cab: List<String>, val linhas: List<String>)
        var ano: Int? = null
        val blocos = ArrayList<Bloco>()
        for (pg in paginas) {
            val tp = textoDaPagina(pg)
            val txtPg = tp.linhas.joinToString(" ") { it.txt }
            if (ano == null) RX_ANO.find(compacto(txtPg))?.let { ano = it.groupValues[1].toInt() }
            blocos.add(Bloco(tp.cab.map { it.txt }, tp.linhas.map { it.txt }))
        }
        if (ano == null) ano = Regex("(20\\d\\d)(\\d\\d)").find(nomeArquivo)?.groupValues?.get(1)?.toInt() ?: anoAtual
        val idioma = detectarIdioma(blocos.joinToString(" ") { it.linhas.joinToString(" ") })

        class Fatia(val m: MatchResult) { val linhas = ArrayList<String>() }
        val fatias = ArrayList<Fatia>()
        var atual: Fatia? = null
        for (b in blocos) {
            val cabTxt = compacto(b.cab.joinToString("|"))
            val ic = RX_INI_CAB.find(cabTxt)?.range?.first ?: -1
            val cabSem = if (ic >= 0) RX_CAB.find(cabTxt.substring(ic)) else null
            if (cabSem != null) { atual = Fatia(cabSem); fatias.add(atual) }
            atual?.linhas?.addAll(b.linhas)
        }

        val out = ArrayList<Semana>()
        var mesAnterior: Int? = null
        var anoCorr = ano!!
        val nm = NOMES_MES.getValue(idioma)
        for (f in fatias) {
            val g = f.m.groupValues
            val d1 = g[1].toInt(); val mes1 = g[2]; val d2 = g[3].toInt(); val mes2 = g[4]; val resto = g[5]
            val m2 = MESES.getValue(mes2)
            val m1 = if (mes1.isNotEmpty()) MESES.getValue(mes1) else m2
            if (mesAnterior != null && m1 < mesAnterior) anoCorr++
            mesAnterior = m2
            val txt = f.linhas.joinToString("\n")
            val partes = analisarSemana(f.linhas)
            if (partes.none { it.n == 1 } || partes.size < 5) continue
            val cantE = cantico(RX_CANT_ORACAO, txt)
            val meio = f.linhas.firstNotNullOfOrNull { RX_CANT_SO.find(it) }?.groupValues?.get(1)?.toInt()
            val inicio = LocalDate.of(anoCorr, m1, d1).toString()
            val periodo = if (mes1.isNotEmpty() && m1 != m2)
                "$d1 de ${nm[m1]} ${if (idioma == "es") "a" else "–"} $d2 de ${nm[m2]}"
            else "$d1–$d2 de ${nm[m2]}"
            out.add(Semana(
                id = "$idioma-$inicio", idioma = idioma, inicio = inicio, periodo = periodo,
                leitura = tituloLivro(resto),
                canticoInicial = cantE.firstOrNull(),
                canticoMeio = meio,
                canticoFinal = if (cantE.size > 1) cantE.last() else null,
                partes = partes, origem = nomeArquivo,
            ))
        }
        for (i in 0 until out.size - 1) out[i] = out[i].copy(proxima = resumoProxima(out[i + 1]))
        return ResultadoLeitura(idioma, ano!!, out)
    }

    fun resumoProxima(s: Semana): Proxima {
        val t1 = s.partes.firstOrNull { it.n == 1 }
        val vida = s.partes.filter { it.sec == "vida" && it.tipo != "ebc" }.map { it.titulo }
        val ebc = s.partes.firstOrNull { it.tipo == "ebc" }
        return Proxima(s.periodo, s.leitura, t1?.titulo ?: "", vida, ebc?.materia ?: "")
    }
}
