package app.roteiro.presidente.nucleo

/* Roteiros do fim de semana (Reunião Pública e Estudo de A Sentinela), da visita do SC,
   do discurso especial (S-125) e da Celebração (S-126). */

/** Chaves dos valores guardados no estado de cada fim de semana / Celebração. */
object CamposFds {
    const val DISC = "discurso"
    const val TEMA = "tema"
    const val ORADOR = "orador"
    const val CONG = "cong"
    const val CANT = "cantico"
    const val ORA_INI = "oracaoIni"
    const val DIR = "dirigente"
    const val LEITOR = "leitor"
    const val CANT_S = "canticoS"
    const val CANT_F = "canticoF"
    const val ORA_FIM = "oracaoFim"
    const val ARTIGO = "artigo"
    const val SC = "scNome"
    const val SC_TEMA = "scTema"
    const val SC_TEMA_F = "scTemaF"
    const val ESPECIAL = "especial"
    const val DATA = "data"
    const val HORA = "hora"
    const val INTRO = "intro"
    const val AVISOS = "avisos"
}

/** Resumo de um fim de semana (agenda e anúncio da próxima semana). */
data class ResumoFds(
    val semana: String,         // segunda-feira AAAA-MM-DD
    val discurso: String,
    val tema: String,
    val orador: String,
    val cong: String,
    val sc: Boolean,
    val especial: Boolean,
) {
    val vazio: Boolean get() = !sc && !especial && tema.isBlank() && orador.isBlank()
}

object RoteiroFds {
    private val RX_CAMPO = Regex("""\[([^\]]+)\]""")

    fun resumo(semana: String, e: EstadoSemana) = ResumoFds(
        semana, e.valor(CamposFds.DISC).trim(), e.valor(CamposFds.TEMA).trim(), e.valor(CamposFds.ORADOR).trim(),
        e.valor(CamposFds.CONG).trim(), e.sc, e.marcado(CamposFds.ESPECIAL),
    )

    private class Montador(val inicio: Int, val total: Int, val totalTxt: String) {
        val itens = ArrayList<ItemRoteiro>()
        val passos = ArrayList<Passo>()
        var t = inicio
        fun secao(sec: String, titulo: String) { itens.add(ItemRoteiro.Secao(sec, titulo)) }
        fun dica(txt: String, sc: Boolean = false) { itens.add(ItemRoteiro.Dica(txt, sc)) }
        fun add(b: Bloco, dur: Int, ultimo: Boolean = false) {
            val p = Passo(b.id, b.titulo, dur, false, t)
            itens.add(ItemRoteiro.BlocoR(b.copy(hora = Roteiro.hm(t), ini = t, passos = listOf(p), totalTxt = totalTxt,
                durTxt = b.durTxt.ifEmpty { if (dur > 0) "$dur min" else "" },
                termino = if (ultimo) Roteiro.hm(inicio + total) else b.termino)))
            passos.add(p)
            t += dur
        }
        fun pronto() = RoteiroMontado(itens, passos, Roteiro.hm(inicio + total), total)
    }

    /** Cumprimento conforme o horário: bom dia / boa tarde / boa noite. */
    fun saudacao(inicio: String, s: Textos.Idioma): String {
        val h = Roteiro.minDe(inicio) / 60
        return s[if (h < 12) "saud_m" else if (h < 18) "saud_t" else "saud_n"]
    }

    /** Cântico como é anunciado: "21, “Título”, baseado em Mateus 6:33". */
    private fun cantico(n: Int, lang: String, titulo: (Int) -> String?, texto: (Int) -> String?): Pair<String, String?> {
        val s = Textos.de(lang)
        val q = if (lang == "es") "«" to "»" else "“" to "”"
        val tit = titulo(n)?.takeIf { it.isNotBlank() }
        val base = texto(n)?.takeIf { it.isNotBlank() }
        val falado = buildString {
            append(n)
            if (tit != null) append(", ").append(q.first).append(tit).append(q.second)
            if (base != null) append(", ").append(Textos.formatar(s["cant_base"], "t" to base))
        }
        return falado to listOfNotNull(tit, base).joinToString("  ·  ").ifEmpty { null }
    }

    /** Anúncio com os espaços entre colchetes virando campos ("anuncio.0", "anuncio.1"…). */
    fun anuncio(texto: String): List<Seg> {
        val out = ArrayList<Seg>()
        var pos = 0
        var i = 0
        for (m in RX_CAMPO.findAll(texto)) {
            if (m.range.first > pos) out.add(Seg.Txt(texto.substring(pos, m.range.first)))
            out.add(Seg.Nome("anuncio.$i", m.groupValues[1]))
            i++
            pos = m.range.last + 1
        }
        if (pos < texto.length) out.add(Seg.Txt(texto.substring(pos)))
        return out
    }

    private fun bloco(id: String, titulo: String, falas: List<List<Seg>> = emptyList(), instr: List<String> = emptyList(),
                      sec: String? = null, estilo: Estilo = Estilo.NORMAL, subtitulo: String? = null, campos: List<Campo> = emptyList()) =
        Bloco(id = id, hora = "", ini = 0, titulo = titulo, subtitulo = subtitulo, sec = sec, estilo = estilo,
            falas = falas, instr = instr, campos = campos)

    /**
     * Roteiro do fim de semana.
     * @param especial orientações do discurso especial (usadas quando o fim de semana está marcado como discurso especial)
     * @param proxima resumo do fim de semana seguinte (para anunciar o próximo discurso)
     */
    fun montar(
        lang: String,
        e: EstadoSemana,
        inicio: String,
        especial: EventoEspecial?,
        proxima: ResumoFds?,
        tituloCantico: (Int) -> String? = { null },
        textoCantico: (Int) -> String? = { null },
        intro: String? = null,
        avisos: String? = null,
    ): RoteiroMontado {
        val s = Textos.de(lang)
        val q = if (lang == "es") "«" to "»" else "“" to "”"
        val sc = e.sc
        val esp = !sc && e.marcado(CamposFds.ESPECIAL)
        val ev = if (esp) especial else null
        val m = Montador(Roteiro.minDe(inicio), 105, "1h45")
        fun num(k: String) = e.valor(k).trim().toIntOrNull()
        fun nome(k: String, rot: String) = Campo(k, rot)

        // cântico: texto completo quando há número; senão, um espaço em branco
        fun cant(k: String, fixo: Int? = null): Triple<Map<String, String>, Map<String, Campo>, String?> {
            val n = fixo ?: num(k)
            return if (n != null) { val (f, sub) = cantico(n, lang, tituloCantico, textoCantico); Triple(mapOf("n" to f), emptyMap(), sub) }
            else Triple(emptyMap(), mapOf("n" to nome(k, s["f_cantico_n"])), null)
        }
        fun tituloCant(modelo: String, n: Int?) = Textos.formatar(s[modelo], "n" to (n?.toString() ?: "")).replace("  ", " ").trim()

        m.secao("publica", s.sec["publica"].orEmpty())

        // 1. Cântico e oração
        val nIni = ev?.cantico ?: num(CamposFds.CANT)
        val (tIni, nmIni, subIni) = cant(CamposFds.CANT, ev?.cantico)
        m.add(bloco("abertura", tituloCant("cantico_oracao", nIni), subtitulo = subIni, sec = "publica",
            falas = if (intro.isNullOrBlank()) listOf(Roteiro.fala(s["fds_abre_fala"], tIni + ("saud" to saudacao(inicio, s)),
                nmIni + ("f" to nome(CamposFds.ORA_INI, s["f_oracaoIni"]))))
            else listOf(listOf(Seg.Txt(intro)), Roteiro.fala(s["cant_ora_fala"], tIni, nmIni + ("f" to nome(CamposFds.ORA_INI, s["f_oracaoIni"])))),
            instr = listOfNotNull(ev?.notas?.firstOrNull())), 5)

        // 2. Discurso público (do orador, do SC ou o discurso especial)
        val temaTxt = e.valor(CamposFds.TEMA).trim()
        val nDisc = e.valor(CamposFds.DISC).trim()
        when {
            sc -> m.add(bloco("discurso", s["fds_disc_t"], sec = "publica", estilo = Estilo.SC,
                subtitulo = e.valor(CamposFds.SC_TEMA).trim().ifBlank { null },
                falas = listOf(Roteiro.fala(s["fds_sc_apres"], nomes = mapOf("sc" to nome(CamposFds.SC, s["f_sc_nome"]),
                    "tema" to nome(CamposFds.SC_TEMA, s["f_scTema"]))))), 30)
            ev != null -> m.add(bloco("discurso", s["fds_t_esp"], sec = "publica", subtitulo = ev.tema,
                falas = listOf(Roteiro.fala(s["esp_apres"], mapOf("tema" to ev.tema),
                    mapOf("orador" to nome(CamposFds.ORADOR, s["f_orador_"]), "cong" to nome(CamposFds.CONG, s["f_cong"]))))), 30)
            else -> m.add(bloco("discurso", s["fds_disc_t"], sec = "publica",
                subtitulo = listOfNotNull(nDisc.ifBlank { null }?.let { "nº $it" }, temaTxt.ifBlank { null }).joinToString(" · ").ifBlank { null },
                falas = listOf(Roteiro.fala(s["fds_apres"], nomes = mapOf("orador" to nome(CamposFds.ORADOR, s["f_orador_"]),
                    "cong" to nome(CamposFds.CONG, s["f_cong"]), "tema" to nome(CamposFds.TEMA, s["f_tema"]))))), 30)
        }

        // 3. Anúncio do discurso especial
        if (ev != null && ev.anuncio.isNotBlank()) {
            val segs = anuncio(ev.anuncio)
            m.add(bloco("anuncio", s["esp_anuncio_t"], sec = "publica", instr = listOf(s["esp_anuncio_instr"]),
                falas = listOf(segs), campos = Roteiro.nomesDe(listOf(segs))).copy(falaNoFim = true), 2)
        }

        // 4. Depois do discurso (presidente): agradecimento, próxima semana e apresentação do dirigente de A Sentinela
        val temAnuncio = ev != null && ev.anuncio.isNotBlank()
        val falasDepois = ArrayList<List<Seg>>()
        val instrDepois = ArrayList<String>()
        falasDepois.add(if (sc) Roteiro.fala(s["fds_sc_agr"], nomes = mapOf("sc" to nome(CamposFds.SC, s["f_sc_nome"])))
                        else Roteiro.fala(s["fds_agr"], nomes = mapOf("orador" to nome(CamposFds.ORADOR, s["f_orador_"]))))
        when {
            proxima == null || proxima.vazio -> instrDepois.add(s["fds_prox_vazio"])
            proxima.sc -> falasDepois.add(Roteiro.fala(s["fds_prox_sc"]))
            proxima.especial -> falasDepois.add(Roteiro.fala(s["fds_prox_esp"], mapOf("tema" to (especial?.tema ?: ""))))
            else -> falasDepois.add(Roteiro.fala(s["fds_prox"], mapOf("tema" to proxima.tema.ifBlank { "________" },
                "orador" to proxima.orador.ifBlank { "________" }, "cong" to proxima.cong.ifBlank { "________" })))
        }
        avisos?.trim()?.takeIf { it.isNotEmpty() }?.let { falasDepois.add(listOf(Seg.Txt(it))) }
        falasDepois.add(Roteiro.fala(s["fds_sent_fala"], nomes = mapOf("dir" to nome(CamposFds.DIR, s["f_dir_s"]))))
        ev?.notas?.drop(1)?.lastOrNull()?.let { instrDepois.add(it) }
        m.add(bloco("depois", s["fds_depois_t"], sec = "publica", falas = falasDepois, instr = instrDepois), if (temAnuncio) 1 else 2)

        // 5. Estudo de A Sentinela — conduzido pelo dirigente: cântico, estudo e encerramento
        m.secao("sentinela", s.sec["sentinela"].orEmpty())
        val dir = e.valor(CamposFds.DIR).ifBlank { "________" }
        val leitor = e.valor(CamposFds.LEITOR).ifBlank { "________" }
        val nS = num(CamposFds.CANT_S)
        val subS = nS?.let { cantico(it, lang, tituloCantico, textoCantico).second }
        m.add(bloco("cantS", tituloCant("cantico", nS), sec = "sentinela", subtitulo = subS,
            instr = listOf(Textos.formatar(s["fds_cant_instr"], "dir" to dir))), if (temAnuncio) 2 else 3)
        m.add(bloco("sentinela", s["fds_sent_t"], sec = "sentinela", subtitulo = e.valor(CamposFds.ARTIGO).trim().ifBlank { null },
            instr = listOfNotNull(Textos.formatar(s["fds_sent_instr"], "dir" to dir, "leitor" to leitor),
                if (sc) s["fds_sc_sent_instr"] else null)), if (sc) 30 else 60)

        // 6. Discurso de serviço do SC (apresentado pelo dirigente de A Sentinela)
        if (sc) m.add(bloco("scFinal", s["fds_sc_final_t"], estilo = Estilo.SC, sec = "sentinela",
            subtitulo = e.valor(CamposFds.SC_TEMA_F).trim().ifBlank { null },
            instr = listOf(Textos.formatar(s["fds_sc_final_instr"], "dir" to dir, "sc" to e.valor(CamposFds.SC).ifBlank { "________" }))), 30)

        // 7. Cântico final e oração (o dirigente encerra a reunião)
        val nF = num(CamposFds.CANT_F)
        val subF = nF?.let { cantico(it, lang, tituloCantico, textoCantico).second }
        m.add(bloco("fim", tituloCant("cantico_oracao", nF), subtitulo = subF, sec = "sentinela",
            instr = listOfNotNull(Textos.formatar(s["fds_fim_instr"], "dir" to dir),
                Textos.formatar(s["fds_ora_instr"], "f" to e.valor(CamposFds.ORA_FIM).ifBlank { "________" }),
                if (sc) s["fds_sc_fim_instr"] else null)), 5, ultimo = true)
        return m.pronto()
    }

    /** Roteiro da Celebração (S-126). */
    fun montarCelebracao(
        lang: String,
        e: EstadoSemana,
        inicio: String,
        ev: EventoEspecial?,
        tituloCantico: (Int) -> String? = { null },
        textoCantico: (Int) -> String? = { null },
        intro: String? = null,
        avisos: String? = null,
    ): RoteiroMontado {
        val s = Textos.de(lang)
        val m = Montador(Roteiro.minDe(inicio), 58, "58 min")
        fun nome(k: String, rot: String) = Campo(k, rot)
        fun cant(n: Int?): Pair<String, String?> = if (n != null) cantico(n, lang, tituloCantico, textoCantico) else "________" to null
        fun tituloCant(modelo: String, n: Int?) = Textos.formatar(s[modelo], "n" to (n?.toString() ?: "")).replace("  ", " ").trim()
        val tema = ev?.tema.orEmpty()

        m.secao("celebracao", s["cel_meeting"])
        val (fIni, subIni) = cant(ev?.cantico)
        m.add(bloco("abertura", tituloCant("cantico_oracao", ev?.cantico), subtitulo = subIni, sec = "celebracao",
            falas = if (intro.isNullOrBlank()) listOf(Roteiro.fala(s["cel_abre_fala"], mapOf("n" to fIni, "saud" to saudacao(inicio, s)),
                mapOf("f" to nome(CamposFds.ORA_INI, s["f_oracaoIni"]))))
            else listOf(listOf(Seg.Txt(intro)), Roteiro.fala(s["cant_ora_fala"], mapOf("n" to fIni), mapOf("f" to nome(CamposFds.ORA_INI, s["f_oracaoIni"])))),
            instr = listOfNotNull(ev?.notas?.firstOrNull())), 5)
        m.add(bloco("discurso", s["cel_disc_t"], sec = "celebracao", subtitulo = tema.ifBlank { null },
            falas = listOf(Roteiro.fala(s["cel_apres"], mapOf("tema" to tema), mapOf("orador" to nome(CamposFds.ORADOR, s["f_orador_"]))))), 45)
        if (ev != null && ev.anuncio.isNotBlank()) {
            val segs = anuncio(ev.anuncio)
            m.add(bloco("anuncio", s["cel_anuncio_t"], sec = "celebracao", instr = listOf(s["esp_anuncio_instr"]),
                falas = listOf(segs) + listOfNotNull(avisos?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(Seg.Txt(it)) }),
                campos = Roteiro.nomesDe(listOf(segs))).copy(falaNoFim = true), 3)
        }
        val (fFim, subFim) = cant(ev?.canticoFinal)
        m.add(bloco("fim", tituloCant("cantico_oracao", ev?.canticoFinal), subtitulo = subFim, sec = "celebracao",
            falas = listOf(Roteiro.fala(s["cel_fim_fala"], mapOf("n" to fFim), mapOf("orador" to nome(CamposFds.ORADOR, s["f_orador_"])))),
            instr = listOfNotNull(ev?.notas?.drop(1)?.lastOrNull())), 5, ultimo = true)
        return m.pronto()
    }
}
