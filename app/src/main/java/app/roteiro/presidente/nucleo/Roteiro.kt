package app.roteiro.presidente.nucleo

/* Monta o roteiro do presidente para uma semana: blocos com horário, falas, instruções do S-38,
   campos de nomes, conselhos com os pontos da lição e os passos do cronômetro. */

/** Trecho de uma fala: texto fixo ou o nome de um designado (campo editável). */
sealed interface Seg {
    data class Txt(val s: String) : Seg
    data class Nome(val k: String, val rotulo: String) : Seg
}

data class Campo(val k: String, val rotulo: String, val area: Boolean = false)

data class PontoCons(val k: String?, val titulo: String?, val texto: String, val destaque: Boolean = false)

data class Conselho(
    val notaK: String,
    val licao: String?,
    val licaoTitulo: String?,
    val secaoLmd: String?,
    val objetivo: String?,
    val aprendemos: String?,
    val pontos: List<PontoCons>,
    val outros: List<PontoCons>,
    val observacao: String?,
)

enum class Estilo { NORMAL, SALA, SC }

data class Passo(val bloco: String, val titulo: String, val dur: Int, val cons: Boolean, val ini: Int)

data class Bloco(
    val id: String,
    val hora: String,
    val ini: Int,
    val titulo: String,
    val subtitulo: String? = null,
    val tag: String? = null,
    val durTxt: String = "",
    val sec: String? = null,
    val estilo: Estilo = Estilo.NORMAL,
    val falas: List<List<Seg>> = emptyList(),
    val instr: List<String> = emptyList(),     // texto com <b>…</b>
    val lista: List<String> = emptyList(),     // itens com <b>…</b> / <i>…</i>
    val campos: List<Campo> = emptyList(),     // nomes (na ordem em que aparecem)
    val areas: List<Campo> = emptyList(),      // anotações de várias linhas
    val salaB: List<Campo> = emptyList(),
    val conselho: Conselho? = null,
    val falaNoFim: Boolean = false,            // fala depois da lista (ex.: apresentar o SC após os anúncios)
    val termino: String? = null,
    val passos: List<Passo> = emptyList(),
)

sealed interface ItemRoteiro {
    data class Secao(val sec: String, val titulo: String) : ItemRoteiro
    data class Dica(val texto: String, val sc: Boolean) : ItemRoteiro
    data class BlocoR(val b: Bloco) : ItemRoteiro
}

data class RoteiroMontado(val itens: List<ItemRoteiro>, val passos: List<Passo>, val fim: String)

object Roteiro {

    fun minDe(hhmm: String): Int {
        val p = hhmm.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 19
        val m = p.getOrNull(1)?.toIntOrNull() ?: 30
        return h * 60 + m
    }

    fun hm(min: Int): String {
        val h = Math.floorMod(min / 60, 24)
        val m = Math.floorMod(min, 60)
        return "$h:" + m.toString().padStart(2, '0')
    }

    private val RX_PLACEHOLDER = Regex("\\{(\\w+)\\}")
    private val RX_ASPAS_PONTAS = Regex("^[‘’“”'\"]+|[‘’“”'\"]+$")
    private val RX_TEMA = Regex("(?iu)(?:Tema|Título|Titulo)\\s*:\\s*(.+?)\\.?\\s*$")
    private val RX_LMD_LICAO = Regex("(?iu)lmd\\s+(?:lição|lección)\\s+\\d+")
    private val RX_PONTO = Regex("(?iu)ponto|punto")

    /** Cria uma fala: {chave} vira texto (textos) ou campo de nome (nomes). */
    fun fala(modelo: String, textos: Map<String, String> = emptyMap(), nomes: Map<String, Campo> = emptyMap()): List<Seg> {
        val out = ArrayList<Seg>()
        val sb = StringBuilder()
        var pos = 0
        for (m in RX_PLACEHOLDER.findAll(modelo)) {
            sb.append(modelo, pos, m.range.first)
            val k = m.groupValues[1]
            val nome = nomes[k]
            when {
                nome != null -> {
                    if (sb.isNotEmpty()) { out.add(Seg.Txt(sb.toString())); sb.clear() }
                    out.add(Seg.Nome(nome.k, nome.rotulo))
                }
                textos.containsKey(k) -> sb.append(textos.getValue(k))
                else -> sb.append(m.value)
            }
            pos = m.range.last + 1
        }
        sb.append(modelo.substring(pos))
        if (sb.isNotEmpty()) out.add(Seg.Txt(sb.toString()))
        return out
    }

    private fun nomesDe(falas: List<List<Seg>>): List<Campo> =
        falas.flatten().filterIsInstance<Seg.Nome>().map { Campo(it.k, it.rotulo) }.distinctBy { it.k }

    fun textoProxima(px: Proxima?, lang: String): String {
        val s = Textos.de(lang)
        if (px == null) return "<i>${s["prox_sem"]}</i>"
        val q = if (lang == "es") "«" to "»" else "“" to "”"
        val vida = if (px.vida.isNotEmpty()) " · ${s.sec["vida"]}: ${px.vida.joinToString("; ")}" else ""
        return "<b>${px.periodo}</b> · ${px.leitura} · ${q.first}${px.tema}${q.second}$vida${if (px.ebc.isNotEmpty()) " · " + px.ebc else ""}"
    }

    /** Títulos oficiais importados das brochuras (pub, idioma, número) — opcional. */
    fun interface FonteOficial { fun licao(pub: String, lang: String, n: Int): LicaoOficial? }

    private fun conselho(p: Parte, e: EstadoSemana, lang: String, lic: BaseLicoes, of: FonteOficial): Conselho {
        val s = Textos.de(lang)
        val k = "p${p.n}"
        val l = p.licao
        var titulo: String? = null
        var secao: String? = null
        var objetivo: String? = null
        var aprendemos: String? = null
        val pontos = ArrayList<PontoCons>()
        val outros = ArrayList<PontoCons>()
        if (l != null && l.pub == "th" && lic.th.containsKey(l.n.toString())) {
            val d = lic.th.getValue(l.n.toString()).em(lang)
            val o = of.licao("th", lang, l.n)
            titulo = o?.titulo?.ifBlank { null } ?: d.t
            objetivo = o?.objetivo ?: d.obj
            d.p.forEachIndexed { i, t -> pontos.add(PontoCons("chk.$k.$i", o?.pontos?.get(i.toString())?.let { "$it." }, t)) }
        } else if (l != null && l.pub == "lmd" && lic.lmd.containsKey(l.n.toString())) {
            val dados = lic.lmd.getValue(l.n.toString())
            val d = dados.em(lang)
            val o = of.licao("lmd", lang, l.n)
            titulo = o?.titulo?.ifBlank { null } ?: d.t
            secao = lic.secaoLmd(lang, dados.secao)
            aprendemos = o?.objetivo ?: d.l
            val todos = d.p.entries.sortedBy { it.key.toIntOrNull() ?: 0 }
            val foco = l.ponto?.toString()?.takeIf { d.p.containsKey(it) }
            for ((n, v) in todos) {
                val nomePonto = o?.pontos?.get(n) ?: v.getOrElse(0) { "" }
                val pc = PontoCons("chk.$k.$n", "${s["ponto"]} $n — $nomePonto.", v.getOrElse(1) { "" }, destaque = foco != null)
                if (foco == null || n == foco) pontos.add(pc) else outros.add(pc.copy(k = null))
            }
        }
        var obs: String? = null
        if (p.tipo == "discurso_estudante") {
            if (p.apendiceA != null) obs = lic.apendiceA[lang]
            else if (RX_LMD_LICAO.containsMatchIn(p.detalhe) && !RX_PONTO.containsMatchIn(p.detalhe)) obs = lic.discursoLicao[lang]
        }
        return Conselho("nota.$k", l?.txt, titulo, secao, objetivo, aprendemos, pontos, outros, obs)
    }

    /**
     * @param proxima resumo da semana seguinte (para os comentários finais)
     * @param tituloCantico título do cântico pelo número (ou null)
     */
    fun montar(
        sem: Semana,
        e: EstadoSemana,
        cfg: Config,
        lic: BaseLicoes,
        proxima: Proxima?,
        tituloCantico: (Int) -> String? = { null },
        oficial: FonteOficial = FonteOficial { _, _, _ -> null },
    ): RoteiroMontado {
        val lang = sem.idioma
        val s = Textos.de(lang)
        val q = if (lang == "es") "«" to "»" else "“" to "”"
        val sc = e.sc
        val salas = e.salas && !sc
        val itens = ArrayList<ItemRoteiro>()
        val passos = ArrayList<Passo>()
        val inicio = minDe(cfg.inicio)
        var t = inicio

        fun add(b: Bloco, durs: List<Pair<Int, Boolean>>) {
            var ini = t
            val ps = durs.map { (dur, cons) ->
                val tit = (if (cons) s["cons_t"] + " — " else "") + b.titulo
                Passo(b.id, tit, dur, cons, ini).also { ini += dur }
            }
            val total = durs.sumOf { it.first }
            val bb = b.copy(hora = hm(t), ini = t, passos = ps,
                durTxt = b.durTxt.ifEmpty { if (total > 0) "$total min" else "" })
            itens.add(ItemRoteiro.BlocoR(bb))
            passos.addAll(ps)
            t += total
        }
        fun bloco(id: String, titulo: String, falas: List<List<Seg>> = emptyList(), instr: List<String> = emptyList(),
                  areas: List<Campo> = emptyList(), extras: List<Campo> = emptyList(), sec: String? = null,
                  estilo: Estilo = Estilo.NORMAL, subtitulo: String? = null, lista: List<String> = emptyList()) =
            Bloco(id = id, hora = "", ini = 0, titulo = titulo, subtitulo = subtitulo, sec = sec, estilo = estilo,
                falas = falas, instr = instr, areas = areas, campos = nomesDe(falas) + extras, lista = lista)

        fun cant(n: Int?): Pair<String, String?> {
            if (n == null) return "?" to null
            val tit = tituloCantico(n)
            return (if (tit.isNullOrBlank()) n.toString() else "$n, ${q.first}$tit${q.second}") to tit?.takeIf { it.isNotBlank() }
        }
        fun cfLista(soSC: Boolean): List<String> {
            val itensCf = s.lista("cf_itens").map { Textos.formatar(it, "prox" to textoProxima(proxima, lang)) }
            return if (soSC) listOf(itensCf[0], itensCf[1], itensCf[3]) else itensCf
        }

        val partes = sem.partes
        val vidaPrim = partes.firstOrNull { it.sec == "vida" && it.tipo == "consideracao" }
        val (cIni, tIni) = cant(sem.canticoInicial)
        add(bloco("abertura", Textos.formatar(s["cantico_oracao"], "n" to (sem.canticoInicial ?: "?")),
            subtitulo = tIni,
            falas = listOf(fala(s["abre_fala"], mapOf("meeting" to s["meeting"], "n" to cIni), mapOf("f" to Campo("oracaoIni", s["f_oracao"]))))),
            listOf(cfg.abertura.coerceAtLeast(1) to false))
        val t1 = partes.firstOrNull { it.n == 1 }?.titulo.orEmpty()
        val tv = if (vidaPrim != null) Textos.formatar(s["ci_tv"], "t" to vidaPrim.titulo.replace(RX_ASPAS_PONTAS, "")) else ""
        add(bloco("ci", s["ci_t"],
            falas = listOf(fala(s["ci_fala"], mapOf("leitura" to sem.leitura, "t1" to t1, "tv" to tv))),
            instr = listOf(s["ci_instr"]), areas = listOf(Campo("pontos", s["f_pontos"], true))),
            listOf(1 to false))

        var secAtual: String? = null
        for (p in partes) {
            if (p.sec != secAtual) {
                if (p.sec == "vida" && salas) add(bloco("salasVolta", s["salas_volta_t"], instr = listOf(s["salas_volta_instr"]), estilo = Estilo.SALA), listOf(0 to false))
                secAtual = p.sec
                itens.add(ItemRoteiro.Secao(p.sec, s.sec[p.sec].orEmpty()))
                if (p.sec == "ministerio") itens.add(ItemRoteiro.Dica(s["min_tip"], false))
                if (p.sec == "vida") {
                    val (cM, tM) = cant(sem.canticoMeio)
                    add(bloco("meio", Textos.formatar(s["cantico"], "n" to (sem.canticoMeio ?: "?")), subtitulo = tM,
                        falas = listOf(fala(s["meio_fala"], mapOf("n" to cM)))), listOf(cfg.meio.coerceAtLeast(1) to false))
                }
            }
            val k = "p${p.n}"
            val tit = "${p.n}. ${p.titulo}"
            val tituloLimpo = p.titulo.replace(RX_ASPAS_PONTAS, "")
            when (p.tipo) {
                "discurso" -> add(bloco(k, tit, sec = p.sec,
                    falas = listOf(fala(s["disc_fala"], mapOf("t" to p.titulo), mapOf("f" to Campo("$k.orador", s["f_orador"]))))),
                    listOf(p.min to false))
                "joias" -> {
                    add(bloco(k, tit, sec = p.sec,
                        falas = listOf(fala(s["joias_fala"], mapOf("t" to p.titulo), mapOf("f" to Campo("$k.dir", s["f_dirigente"]))))),
                        listOf(p.min to false))
                    if (salas) add(bloco("salasIda", s["salas_ida_t"], estilo = Estilo.SALA,
                        falas = listOf(fala(s["salas_ida_fala"])), instr = listOf(s["salas_ida_instr"]),
                        extras = listOf(Campo("dirB", s["f_dir_b"]))), listOf(0 to false))
                    else if (sc) itens.add(ItemRoteiro.Dica(s["salas_sc"], true))
                }
                "leitura", "estudante", "discurso_estudante" -> {
                    val falas: List<List<Seg>>
                    val instr = ArrayList<String>()
                    val est = Campo("$k.est", s["f_estudante"])
                    when (p.tipo) {
                        "leitura" -> falas = listOf(fala(s["leit_fala"], mapOf("d" to (p.passagem ?: p.detalhe)), mapOf("f" to est)))
                        "estudante" -> {
                            falas = listOf(fala(s["est_fala"], mapOf("t" to p.titulo), mapOf("f1" to est, "f2" to Campo("$k.aj", s["f_ajudante"]))))
                            val desc = listOfNotNull(p.cena, p.texto?.takeIf { it.isNotBlank() }).joinToString(". ")
                            if (desc.isNotBlank()) instr.add("<b>${if (p.cena != null) s["cena"] else s["parte"]}:</b> $desc")
                        }
                        else -> {
                            val m = RX_TEMA.find(p.texto ?: p.detalhe)
                            falas = listOf(if (m != null) fala(s["est_disc_fala"], mapOf("tema" to m.groupValues[1]), mapOf("f" to est))
                                           else fala(s["est_disc_fala2"], nomes = mapOf("f" to est)))
                            if (!p.texto.isNullOrBlank()) instr.add(p.texto)
                        }
                    }
                    val salaB = if (salas) listOfNotNull(Campo("$k.estB", s["f_estudante"]),
                        if (p.tipo == "estudante") Campo("$k.ajB", s["f_ajudante"]) else null) else emptyList()
                    add(bloco(k, tit, falas = falas, instr = instr, sec = p.sec).copy(
                        tag = s["estudante"], durTxt = "${p.min} + 1 min", salaB = salaB, conselho = conselho(p, e, lang, lic, oficial)),
                        listOf(p.min to false, 1 to true))
                }
                "consideracao_min" -> add(bloco(k, tit, sec = p.sec,
                    falas = listOf(fala(s["consid_fala"], mapOf("t" to p.titulo), mapOf("f" to Campo("$k.orador", s["f_orador"])))),
                    instr = listOfNotNull(p.texto?.takeIf { it.isNotBlank() }, s["oqvd_obs"])), listOf(p.min to false))
                "consideracao" -> add(bloco(k, tit, sec = p.sec,
                    falas = listOf(fala(s["consid_fala"], mapOf("t" to tituloLimpo), mapOf("f" to Campo("$k.orador", s["f_orador"])))),
                    instr = listOf(s["consid_obs"])), listOf(p.min to false))
                "necessidades" -> add(bloco(k, tit, sec = p.sec,
                    falas = listOf(fala(s["nec_fala"], nomes = mapOf("f" to Campo("$k.orador", s["f_orador"])))),
                    instr = listOf(s["nec_obs"])), listOf(p.min to false))
                "ebc" -> {
                    if (sc) {
                        add(bloco("scCf", s["sc_cf_t"], estilo = Estilo.SC, lista = cfLista(true),
                            areas = listOf(Campo("anuncios", s["f_anuncios"], true)),
                            falas = listOf(fala(s["sc_cf_fala"], nomes = mapOf("f" to Campo("sc", s["f_sc"]))))).copy(falaNoFim = true), listOf(3 to false))
                        add(bloco("scDisc", s["sc_disc_t"], estilo = Estilo.SC, sec = "vida", instr = listOf(s["sc_disc_obs"])), listOf(30 to false))
                        add(bloco("scFim", s["sc_fim_t"], estilo = Estilo.SC, instr = listOf(s["sc_fim_obs"]),
                            extras = listOf(Campo("scCantico", s["f_cantico"]))).copy(termino = hm(inicio + 105)),
                            listOf(maxOf(0, inicio + 105 - t) to false))
                    } else add(bloco(k, tit, sec = p.sec,
                        falas = listOf(fala(s["ebc_fala"], mapOf("t" to p.titulo), mapOf("f1" to Campo("$k.dir", s["f_dirigente"]), "f2" to Campo("$k.leitor", s["f_leitor"])))),
                        instr = listOf("${p.materia ?: p.detalhe} · ${s["ebc_instr"]}")), listOf(p.min to false))
                }
            }
        }
        if (!sc) {
            add(bloco("cf", s["cf_t"], lista = cfLista(false), areas = listOf(Campo("anuncios", s["f_anuncios"], true)),
                instr = listOf(s["cf_aviso"])), listOf(3 to false))
            val (cF, tF) = cant(sem.canticoFinal)
            add(bloco("fim", Textos.formatar(s["cantico_oracao"], "n" to (sem.canticoFinal ?: "?")), subtitulo = tF,
                falas = listOf(fala(s["fim_fala"], mapOf("n" to cF), mapOf("f" to Campo("oracaoFim", s["f_oracao"]))))
            ).copy(termino = hm(inicio + 105)), listOf(maxOf(0, inicio + 105 - t) to false))
        }
        return RoteiroMontado(itens, passos, hm(inicio + 105))
    }
}
