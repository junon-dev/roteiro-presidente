package app.roteiro.presidente

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.roteiro.presidente.dados.Arquivo
import app.roteiro.presidente.dados.Avisos
import app.roteiro.presidente.dados.EstadoSemanaDto
import app.roteiro.presidente.dados.Importado
import app.roteiro.presidente.dados.Importador
import app.roteiro.presidente.dados.Repositorio
import app.roteiro.presidente.nucleo.BaseLicoes
import app.roteiro.presidente.nucleo.CamposFds
import app.roteiro.presidente.nucleo.EventoEspecial
import app.roteiro.presidente.nucleo.ResumoFds
import app.roteiro.presidente.nucleo.RoteiroFds
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.EstadoPresidir
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.LeitorApostila
import app.roteiro.presidente.nucleo.LicaoOficial
import app.roteiro.presidente.nucleo.Passo
import app.roteiro.presidente.nucleo.Proxima
import app.roteiro.presidente.nucleo.Relatorio
import app.roteiro.presidente.nucleo.Roteiro
import app.roteiro.presidente.nucleo.RoteiroMontado
import app.roteiro.presidente.nucleo.Semana
import app.roteiro.presidente.nucleo.Textos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppVM(app: Application) : AndroidViewModel(app) {

    val repo = Repositorio(app)
    private val importador = Importador(app)
    val embutidas: List<Semana> = repo.semanasEmbutidas()
    val licoes: BaseLicoes = repo.licoes()
    val oficiaisEmbutidos = repo.oficiaisEmbutidos()
    val canticosEmbutidos = repo.canticosEmbutidos()
    val textosEmbutidos = repo.textosCanticosEmbutidos()
    val eventosEmbutidos = repo.eventosEmbutidos()
    val discursosEmbutidos = repo.discursosEmbutidos()

    var dados by mutableStateOf(repo.carregar())
        private set
    var semanaId by mutableStateOf<String?>(null)
    /** Qual reunião está na tela: "meio" (meio de semana), "fds" (fim de semana) ou "cel" (Celebração). */
    var modo by mutableStateOf(if (LocalDate.now().dayOfWeek.value >= 6) "fds" else "meio")
        private set
    /** Fim de semana escolhido: a segunda-feira (AAAA-MM-DD) da semana. */
    var fdsSemana by mutableStateOf(segundaDe(LocalDate.now()))
        private set
    /** Ano da Celebração na tela. */
    var celAno by mutableStateOf(LocalDate.now().let { if (it.monthValue > 4) it.year + 1 else it.year })
        private set
    var agora by mutableLongStateOf(System.currentTimeMillis())
    var ocupado by mutableStateOf<String?>(null)
        private set
    /** Resultado da última importação (fica na tela até o usuário fechar). */
    var resultado by mutableStateOf<String?>(null)

    private val canalMsg = Channel<String>(Channel.BUFFERED)
    val mensagens = canalMsg.receiveAsFlow()
    fun avisar(m: String) { canalMsg.trySend(m) }

    val cfg: Config get() = dados.cfg
    val tx: Textos.Idioma get() = Textos.de(cfg.lang)

    init {
        escolherSemanaPadrao()
        // Se o app foi fechado no meio da reunião, volta para a mesma semana no modo Presidir
        dados.presidir?.let { pr ->
            val recente = pr.ativo && System.currentTimeMillis() - pr.inicioPasso < 3 * 3600_000L
            val partes = pr.sem.split(":")
            if (recente && partes.size == 3 && partes[0] in listOf("fds", "cel")) {
                if (partes[1] != cfg.lang) dados = dados.copy(cfg = cfg.copy(lang = partes[1]))
                if (partes[0] == "fds") { modo = "fds"; fdsSemana = partes[2] } else { modo = "cel"; celAno = partes[2].toIntOrNull() ?: celAno }
            } else {
                val sem = todasSemanas().firstOrNull { it.id == pr.sem }
                if (recente && sem != null) {
                    if (sem.idioma != cfg.lang) dados = dados.copy(cfg = cfg.copy(lang = sem.idioma))
                    semanaId = sem.id
                    modo = "meio"
                }
            }
        }
    }

    /* ---------- gravação ---------- */
    private var gravacao: Job? = null
    private fun mudar(f: (Arquivo) -> Arquivo) {
        dados = f(dados)
        gravacao?.cancel()
        val copia = dados
        gravacao = viewModelScope.launch(Dispatchers.IO) { delay(300); repo.gravar(copia) }
    }
    fun gravarAgora() { gravacao?.cancel(); repo.gravar(dados) }

    /** Substitui todos os dados (migração da versão anterior ou cópia de segurança). */
    fun substituir(a: Arquivo) {
        mudar { a }
        escolherSemanaPadrao()
    }

    /* ---------- semanas ---------- */
    fun todasSemanas(): List<Semana> {
        val mapa = LinkedHashMap<String, Semana>()
        for (s in embutidas) if (s.id !in dados.removidas) mapa[s.id] = s
        for (s in dados.importadas) mapa[s.id] = s
        return mapa.values.sortedBy { it.inicio }
    }
    fun semanasIdioma(): List<Semana> = todasSemanas().filter { it.idioma == cfg.lang }
    fun semanaAtual(): Semana? = todasSemanas().firstOrNull { it.id == semanaId }
    fun ehEmbutida(id: String) = embutidas.any { it.id == id } && dados.importadas.none { it.id == id }

    fun escolherSemanaPadrao() {
        val lst = semanasIdioma()
        if (lst.isEmpty()) { semanaId = null; return }
        val hoje = LocalDate.now().toString()
        var alvo = lst.lastOrNull { it.inicio <= hoje }
        if (alvo != null && LocalDate.parse(alvo.inicio).plusDays(7).toString() <= hoje) {
            lst.firstOrNull { it.inicio > hoje }?.let { alvo = it }
        }
        semanaId = (alvo ?: lst.first()).id
    }

    fun irParaSemana(id: String) { if (presidindo) pararPresidir(); semanaId = id; modo = "meio" }
    fun moverSemana(d: Int) {
        val lst = semanasIdioma()
        val i = lst.indexOfFirst { it.id == semanaId }
        lst.getOrNull(i + d)?.let { irParaSemana(it.id) }
    }

    fun mudarIdioma(lang: String) {
        if (lang == cfg.lang) return
        val atual = semanaAtual()
        if (presidindo) pararPresidir()
        mudar { it.copy(cfg = it.cfg.copy(lang = lang)) }
        val par = atual?.let { a -> semanasIdioma().firstOrNull { it.inicio == a.inicio } }
        if (par != null) semanaId = par.id else escolherSemanaPadrao()
    }

    fun proximaDe(sem: Semana): Proxima? {
        val iso = LocalDate.parse(sem.inicio).plusDays(7).toString()
        val p = todasSemanas().firstOrNull { it.idioma == sem.idioma && it.inicio == iso } ?: return sem.proxima
        return LeitorApostila.resumoProxima(p)
    }

    /* ---------- estado de cada semana ---------- */
    fun estado(id: String): EstadoSemana = dados.semanas[id]?.paraModelo() ?: EstadoSemana()
    private fun mudarEstado(id: String, f: (EstadoSemana) -> EstadoSemana) =
        mudar { a -> a.copy(semanas = a.semanas + (id to EstadoSemanaDto.de(f(estado(id))))) }

    fun valor(k: String, v: String) {
        val id = idAtual() ?: return
        mudarEstado(id) {
            var m = it.v + (k to v)
            // no fim de semana, o número do discurso preenche o tema pela lista S-99
            if (modo == "fds" && k == CamposFds.DISC) {
                val t = tituloDiscurso(cfg.lang, v.trim().toIntOrNull())
                if (t != null && !naoUse(t)) m = m + (CamposFds.TEMA to t)
            }
            it.copy(v = m)
        }
    }
    fun marcar(k: String, on: Boolean) = valor(k, on.toString())
    fun setSC(on: Boolean) { val id = idAtual() ?: return; mudarEstado(id) { it.copy(sc = on) } }
    fun setSalas(on: Boolean) { val id = idAtual() ?: return; mudarEstado(id) { it.copy(salas = on) } }
    fun limparSemana() { val id = idAtual() ?: return; mudar { it.copy(semanas = it.semanas - id) } }

    /* ---------- reunião na tela (meio de semana, fim de semana ou Celebração) ---------- */
    data class Reuniao(
        val id: String,
        val tipo: String,
        val idioma: String,
        val sup: String,          // linha de cima (reunião · data)
        val titulo: String,       // título grande
        val periodo: String,      // usado no relatório e na impressão
        val rot: RoteiroMontado,
        val estado: EstadoSemana,
        val inicio: String,
    )

    fun idFds(lang: String, seg: String) = "fds:$lang:$seg"
    fun idCel(lang: String, ano: Int) = "cel:$lang:$ano"
    fun idAtual(): String? = when (modo) {
        "fds" -> idFds(cfg.lang, fdsSemana)
        "cel" -> idCel(cfg.lang, celAno)
        else -> semanaId
    }

    fun mudarModo(m: String) {
        if (m == modo) return
        if (presidindo) pararPresidir()
        modo = m
    }

    fun inicioAtual(): String = when (modo) {
        "fds" -> cfg.inicioFds
        "cel" -> estado(idCel(cfg.lang, celAno)).valor(CamposFds.HORA).ifBlank { "19:30" }
        else -> cfg.inicio
    }
    fun definirInicio(hhmm: String) = when (modo) {
        "fds" -> mudarCfg { it.copy(inicioFds = hhmm) }
        "cel" -> valor(CamposFds.HORA, hhmm)
        else -> mudarCfg { it.copy(inicio = hhmm) }
    }

    fun reuniaoAtual(): Reuniao? {
        val s = tx
        val lang = cfg.lang
        return when (modo) {
            "fds" -> {
                val id = idFds(lang, fdsSemana)
                val e = estado(id)
                val data = dataFdsTexto(fdsSemana, lang)
                val tit = when { e.sc -> s["fds_t_sc"]; e.marcado(CamposFds.ESPECIAL) -> s["fds_t_esp"]; else -> s["fds_t_normal"] }
                Reuniao(id, "fds", lang, "${s["fds_meeting"]} · $data", tit, data, roteiroFds(lang, fdsSemana), e, cfg.inicioFds)
            }
            "cel" -> {
                val id = idCel(lang, celAno)
                val e = estado(id)
                val data = e.valor(CamposFds.DATA).ifBlank { celAno.toString() }
                Reuniao(id, "cel", lang, "${s["cel_meeting"]} · $data", Textos.formatar(s["cel_t"], "a" to celAno),
                    Textos.formatar(s["cel_t"], "a" to celAno), roteiroCel(lang, celAno), e, inicioAtual())
            }
            else -> {
                val sem = semanaAtual() ?: return null
                val e = estado(sem.id)
                Reuniao(sem.id, "meio", sem.idioma, "${s["meeting"]} · ${sem.leitura} · ${sem.periodo}",
                    if (e.sc) s["sc"] else s["normal"], "${sem.periodo} (${sem.leitura})", roteiro(sem), e, cfg.inicio)
            }
        }
    }

    /* ---------- fim de semana ---------- */
    fun segundaDe(d: LocalDate): String = d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()
    fun dataReuniaoFds(seg: String): LocalDate = LocalDate.parse(seg).plusDays((cfg.diaFds.coerceIn(6, 7) - 1).toLong())
    private fun localeDe(lang: String) = if (lang == "es") java.util.Locale("es") else java.util.Locale("pt", "BR")
    fun dataFdsTexto(seg: String, lang: String = cfg.lang): String {
        val f = java.time.format.DateTimeFormatter.ofPattern(if (lang == "es") "EEEE d 'de' MMMM" else "EEEE, d 'de' MMMM", localeDe(lang))
        return dataReuniaoFds(seg).format(f)
    }
    fun dataCurta(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

    fun irParaFds(seg: String) { if (presidindo) pararPresidir(); fdsSemana = seg; modo = "fds" }
    fun moverFds(d: Int) = irParaFds(LocalDate.parse(fdsSemana).plusWeeks(d.toLong()).toString())
    fun moverCel(d: Int) { if (presidindo) pararPresidir(); celAno += d }

    fun resumoFds(lang: String, seg: String): ResumoFds = RoteiroFds.resumo(seg, estado(idFds(lang, seg)))

    fun roteiroFds(lang: String, seg: String): RoteiroMontado = RoteiroFds.montar(
        lang, estado(idFds(lang, seg)), cfg.inicioFds, evento("especial", lang),
        resumoFds(lang, LocalDate.parse(seg).plusWeeks(1).toString()),
        tituloCantico = { n -> tituloCantico(lang, n) }, textoCantico = { n -> textoCantico(lang, n) },
    )
    fun roteiroCel(lang: String, ano: Int): RoteiroMontado {
        val e = estado(idCel(lang, ano))
        return RoteiroFds.montarCelebracao(lang, e, e.valor(CamposFds.HORA).ifBlank { "19:30" }, evento("celebracao", lang),
            tituloCantico = { n -> tituloCantico(lang, n) }, textoCantico = { n -> textoCantico(lang, n) })
    }

    /** Semanas da agenda: da atual até 26 semanas à frente (e, se pedido, as 12 anteriores). */
    fun semanasAgenda(anteriores: Boolean): List<String> {
        val base = LocalDate.parse(segundaDe(LocalDate.now()))
        return ((if (anteriores) -12 else 0)..26).map { base.plusWeeks(it.toLong()).toString() }
    }

    /* ---------- temas dos discursos (S-99) e histórico ---------- */
    fun discursos(lang: String): Map<String, String> = discursosEmbutidos[lang].orEmpty() + dados.discursos[lang].orEmpty()
    fun tituloDiscurso(lang: String, n: Int?): String? = n?.let { discursos(lang)[it.toString()] }
    fun naoUse(t: String) = t.trim().startsWith("(")

    /** Datas em que cada discurso foi (ou será) feito: fins de semana do app + datas registradas à mão. */
    fun historico(lang: String): Map<Int, List<LocalDate>> {
        val m = HashMap<Int, MutableSet<LocalDate>>()
        val pref = "fds:$lang:"
        for ((id, dto) in dados.semanas) if (id.startsWith(pref)) {
            val r = RoteiroFds.resumo(id.removePrefix(pref), dto.paraModelo())
            val n = r.discurso.toIntOrNull() ?: continue
            if (r.sc || r.especial) continue
            m.getOrPut(n) { HashSet() }.add(dataReuniaoFds(r.semana))
        }
        dados.historico[lang].orEmpty().forEach { (k, l) ->
            val n = k.toIntOrNull() ?: return@forEach
            l.forEach { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { m.getOrPut(n) { HashSet() }.add(it) } }
        }
        return m.mapValues { it.value.sorted() }
    }
    fun datasManuais(lang: String, n: Int): List<String> = dados.historico[lang]?.get(n.toString()).orEmpty()
    fun registrarData(lang: String, n: Int, d: LocalDate) = mudar { a ->
        val porLang = a.historico[lang].orEmpty()
        val l = (porLang[n.toString()].orEmpty() + d.toString()).distinct().sorted()
        a.copy(historico = a.historico + (lang to (porLang + (n.toString() to l))))
    }
    fun apagarData(lang: String, n: Int, d: String) = mudar { a ->
        val porLang = a.historico[lang].orEmpty()
        val l = porLang[n.toString()].orEmpty() - d
        a.copy(historico = a.historico + (lang to (if (l.isEmpty()) porLang - n.toString() else porLang + (n.toString() to l))))
    }

    /** Aviso ao escolher um discurso: "Não use", feito há poucos meses, ou já na agenda em outra data. */
    fun avisoDiscurso(lang: String, seg: String, n: Int?): String? {
        if (n == null) return null
        val s = tx
        val t = tituloDiscurso(lang, n)
        if (t != null && naoUse(t)) return s["nao_use"]
        val esta = dataReuniaoFds(seg)
        val outras = historico(lang)[n].orEmpty().filter { it != esta }
        val hoje = LocalDate.now()
        val antes = outras.filter { it < esta }.maxOrNull()
        val depois = outras.filter { it > esta && it >= hoje }.minOrNull()
        val msgs = ArrayList<String>()
        if (antes != null) {
            val meses = java.time.temporal.ChronoUnit.MONTHS.between(antes, esta).toInt()
            msgs.add(if (meses < 12) Textos.formatar(s["hist_recente"], "m" to meses, "d" to dataCurta(antes))
                     else Textos.formatar(s["hist_feito"], "d" to dataCurta(antes)))
        }
        if (depois != null) msgs.add(Textos.formatar(s["hist_agendado"], "d" to dataCurta(depois)))
        return msgs.joinToString(" ").ifBlank { null }
    }

    /* ---------- eventos especiais (S-125, S-126) ---------- */
    fun evento(tipo: String, lang: String): EventoEspecial? = dados.eventos[tipo]?.get(lang) ?: eventosEmbutidos[tipo]?.get(lang)
    fun eventoCorrigido(tipo: String, lang: String) = dados.eventos[tipo]?.containsKey(lang) == true
    fun salvarEvento(ev: EventoEspecial) = mudar { a ->
        a.copy(eventos = a.eventos + (ev.tipo to (a.eventos[ev.tipo].orEmpty() + (ev.idioma to ev))))
    }
    fun restaurarEvento(tipo: String, lang: String) = mudar { a ->
        a.copy(eventos = a.eventos + (tipo to (a.eventos[tipo].orEmpty() - lang)))
    }

    fun mudarCfg(f: (Config) -> Config) = mudar { it.copy(cfg = f(it.cfg)) }

    /* ---------- cânticos e títulos oficiais ---------- */
    fun tituloCantico(lang: String, n: Int): String? =
        dados.canticos[lang]?.get(n.toString()) ?: canticosEmbutidos[lang]?.get(n.toString())
    /** Todos os títulos (incluídos no app + importados/corrigidos pelo usuário). */
    fun canticos(lang: String): Map<String, String> = canticosEmbutidos[lang].orEmpty() + dados.canticos[lang].orEmpty()
    /** Texto bíblico base do cântico (ex.: "Apocalipse 4:11"). */
    fun textoCantico(lang: String, n: Int): String? =
        (dados.textosCanticos[lang]?.get(n.toString()) ?: textosEmbutidos[lang]?.get(n.toString()))?.takeIf { it.isNotBlank() }
    fun definirCantico(lang: String, n: Int, titulo: String, texto: String? = null) = mudar { a ->
        val k = n.toString()
        val atual = a.canticos[lang].orEmpty()
        val novo = if (titulo.isBlank()) atual - k else atual + (k to titulo.trim())
        val txAtual = a.textosCanticos[lang].orEmpty()
        val txNovo = when {
            texto == null -> txAtual
            texto.isBlank() || texto.trim() == textosEmbutidos[lang]?.get(k) -> txAtual - k
            else -> txAtual + (k to texto.trim())
        }
        a.copy(canticos = a.canticos + (lang to novo), textosCanticos = a.textosCanticos + (lang to txNovo))
    }
    fun limparCanticos(lang: String) = mudar { it.copy(canticos = it.canticos - lang, textosCanticos = it.textosCanticos - lang) }
    fun oficial(pub: String, lang: String, n: Int): LicaoOficial? =
        dados.oficiais[pub]?.get(lang)?.get(n.toString()) ?: oficiaisEmbutidos[pub]?.get(lang)?.get(n.toString())
    fun qtdOficiais(pub: String, lang: String): Int =
        (oficiaisEmbutidos[pub]?.get(lang).orEmpty().keys + dados.oficiais[pub]?.get(lang).orEmpty().keys).size
    fun oficiaisImportados(pub: String, lang: String): Boolean = !dados.oficiais[pub]?.get(lang).isNullOrEmpty()

    fun roteiro(sem: Semana): RoteiroMontado = Roteiro.montar(
        sem, estado(sem.id), cfg, licoes, proximaDe(sem),
        tituloCantico = { n -> tituloCantico(sem.idioma, n) },
        textoCantico = { n -> textoCantico(sem.idioma, n) },
        oficial = { pub, lang, n -> oficial(pub, lang, n) },
    )

    /* ---------- importação de arquivos ---------- */
    fun importar(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val msgs = ArrayList<String>()
            for (u in uris) {
                val nome = importador.nomeDe(u)
                ocupado = Textos.formatar(tx["ap_lendo"], "f" to nome)
                val r = withContext(Dispatchers.IO) { importador.ler(u) }
                msgs.add(aplicar(r, nome))
            }
            ocupado = null
            if (semanaAtual() == null) escolherSemanaPadrao()
            resultado = msgs.joinToString("\n\n")
        }
    }

    private fun aplicar(r: Importado, nome: String): String {
        val s = tx
        return when (r) {
            is Importado.Apostila -> {
                val ids = r.r.semanas.map { it.id }.toSet()
                mudar { a -> a.copy(importadas = a.importadas.filter { it.id !in ids } + r.r.semanas, removidas = a.removidas - ids) }
                Textos.formatar(s["ap_ok"], "n" to r.r.semanas.size, "f" to nome, "i" to Textos.NOME_IDIOMA[r.r.idioma])
            }
            is Importado.Brochura -> {
                val b = r.b
                mudar { a ->
                    val porPub = a.oficiais[b.pub].orEmpty()
                    val novo = porPub + (b.idioma to b.licoes.mapKeys { it.key.toString() })
                    a.copy(oficiais = a.oficiais + (b.pub to novo))
                }
                Textos.formatar(s["br_ok"], "n" to b.licoes.size, "p" to b.pub, "i" to Textos.NOME_IDIOMA[b.idioma])
            }
            is Importado.Cancioneiro -> {
                mudar { a -> a.copy(canticos = a.canticos + (r.idioma to (a.canticos[r.idioma].orEmpty() + r.titulos)),
                    textosCanticos = a.textosCanticos + (r.idioma to (a.textosCanticos[r.idioma].orEmpty() + r.textos))) }
                Textos.formatar(s["can_ok"], "n" to r.titulos.size, "i" to Textos.NOME_IDIOMA[r.idioma])
            }
            is Importado.CancioneiroErrado -> Textos.formatar(s["can_antigo"], "t" to r.titulo, "s" to r.simbolo)
            is Importado.Evento -> {
                salvarEvento(r.ev)
                Textos.formatar(s["ev_ok"], "t" to (if (r.ev.tipo == "especial") s["ev_esp"] else s["ev_cel"]) + (r.ev.tema.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                    "i" to Textos.NOME_IDIOMA[r.ev.idioma])
            }
            is Importado.Discursos -> {
                mudar { a -> a.copy(discursos = a.discursos + (r.l.idioma to r.l.titulos)) }
                Textos.formatar(s["disc_ok"], "n" to r.l.titulos.size, "i" to Textos.NOME_IDIOMA[r.l.idioma])
            }
            is Importado.Falha -> Textos.formatar(s["ap_erro"], "f" to nome, "e" to r.motivo)
        }
    }

    fun removerSemanas(ids: Collection<String>) {
        mudar { a ->
            a.copy(importadas = a.importadas.filter { it.id !in ids },
                removidas = (a.removidas + ids.filter { id -> embutidas.any { it.id == id } }).distinct())
        }
        if (semanaId in ids) escolherSemanaPadrao()
    }

    fun removerOficiais(pub: String, lang: String) = mudar { a ->
        a.copy(oficiais = a.oficiais + (pub to (a.oficiais[pub].orEmpty() - lang)))
    }

    /* ---------- cópia automática numa pasta (ex.: Google Drive) ---------- */
    private val prefs = app.getSharedPreferences("copia", android.content.Context.MODE_PRIVATE)
    var ultimaCopia by mutableLongStateOf(prefs.getLong("quando", 0L))
        private set

    fun definirPastaBackup(uri: Uri?) {
        val ctx = getApplication<Application>()
        if (uri != null) runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.edit().remove("hash").apply()
        mudarCfg { it.copy(pastaBackup = uri?.toString()) }
        if (uri != null) copiarAgora(forcar = true)
    }

    fun nomePastaBackup(): String? = cfg.pastaBackup?.let { p ->
        runCatching { androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), Uri.parse(p))?.name }.getOrNull() ?: p
    }

    /** Salva uma cópia por dia na pasta escolhida (só se algo mudou) e guarda as 10 mais recentes. */
    fun copiarAgora(forcar: Boolean = false, avisarResultado: Boolean = false) {
        val pasta = cfg.pastaBackup ?: return
        val texto = repo.paraTexto(dados)
        val hash = texto.hashCode()
        if (!forcar && prefs.getInt("hash", 0) == hash) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, Uri.parse(pasta)) ?: error("pasta")
                val nome = "roteiro-presidente-${LocalDate.now()}.json"
                val arq = dir.findFile(nome) ?: dir.createFile("application/json", nome) ?: error("criar")
                ctx.contentResolver.openOutputStream(arq.uri, "wt")!!.use { it.write(texto.toByteArray()) }
                // mantém só as 10 cópias mais recentes
                dir.listFiles().filter { it.name?.startsWith("roteiro-presidente-20") == true }
                    .sortedByDescending { it.name }.drop(10).forEach { it.delete() }
            }.isSuccess
            withContext(Dispatchers.Main) {
                if (ok) {
                    val t = System.currentTimeMillis()
                    prefs.edit().putInt("hash", hash).putLong("quando", t).apply()
                    ultimaCopia = t
                }
                if (avisarResultado) resultado = if (ok) tx["bk_ok"] else tx["bk_erro"]
            }
        }
    }

    /* ---------- cópia de segurança ---------- */
    fun exportarTexto(): String = repo.paraTexto(dados)
    fun importarTexto(t: String): Boolean = try { substituir(repo.deTexto(t)); true } catch (e: Exception) { false }

    /* ---------- modo Presidir ---------- */
    val presidindo: Boolean get() = dados.presidir?.ativo == true && dados.presidir?.sem == idAtual()
    val presidir: EstadoPresidir? get() = dados.presidir
    private var avisado = false

    /* Relógio da reunião ao começar atrasado ou por uma parte do meio */
    data class PedidoRelogio(val minHorario: Int?, val minSoma: Int?, val horario: String)
    var pedidoRelogio by mutableStateOf<PedidoRelogio?>(null)
    private var perguntouRelogio = false

    /** Minutos desde o horário marcado para a reunião (ex.: 20:00), se a reunião já devia ter começado. */
    private fun minutosDesdeHorario(): Int? {
        val t = java.time.LocalTime.now()
        val m = t.hour * 60 + t.minute - Roteiro.minDe(inicioAtual())
        return m.takeIf { it in 2..170 }
    }

    /** Pergunta (uma vez por reunião) se o relógio deve contar o tempo já passado. */
    private fun verificarRelogio(alvo: Int) {
        if (perguntouRelogio) return
        val p = passosDaSemana().getOrNull(alvo) ?: return
        val jaContado = decorridoReuniao() / 60
        val soma = (p.ini - Roteiro.minDe(inicioAtual())).takeIf { it - jaContado >= 3 }
        val hor = minutosDesdeHorario()?.takeIf { it - jaContado >= 3 }
        if (soma == null && hor == null) return
        perguntouRelogio = true
        pedidoRelogio = PedidoRelogio(hor, soma, inicioAtual())
    }

    /** Faz o relógio da reunião contar [minutos] já passados (null = manter a partir de agora). */
    fun responderRelogio(minutos: Int?) {
        pedidoRelogio = null
        val p = dados.presidir ?: return
        if (minutos == null) return
        val ini = System.currentTimeMillis() - minutos * 60_000L
        mudar { a ->
            val rel = a.relatorios[p.sem]
            a.copy(presidir = p.copy(inicioReuniao = ini),
                relatorios = if (rel != null) a.relatorios + (p.sem to rel.copy(inicio = ini)) else a.relatorios)
        }
    }

    /** Relógio do modo Presidir (desligado só nos testes que geram imagens das telas). */
    var relogioAutomatico = true

    /** Modo palco: esconde os campos de digitação durante a reunião. */
    var palco by mutableStateOf(dados.cfg.palco)

    fun iniciarPresidir(totalPassos: Int) {
        val id = idAtual() ?: return
        val salvo = dados.presidir
        val agoraMs = System.currentTimeMillis()
        val retomando = salvo != null && salvo.sem == id && salvo.passo < totalPassos && agoraMs - salvo.inicioPasso < 3 * 3600_000L
        val novo = if (retomando) salvo!!.copy(ativo = true, inicioReuniao = salvo.inicioReuniao.takeIf { it > 0 } ?: salvo.inicioPasso)
        else EstadoPresidir(id, 0, agoraMs, null, 0, true, inicioReuniao = agoraMs)
        avisado = false
        palco = cfg.palco
        perguntouRelogio = retomando
        mudar { a ->
            val rel = a.relatorios[id]
            val novoRel = if (retomando && rel != null) rel.copy(fim = null) else Relatorio(id, novo.inicioReuniao)
            a.copy(presidir = novo, relatorios = a.relatorios + (id to novoRel))
        }
        agora = System.currentTimeMillis()
        if (!retomando) verificarRelogio(0)
    }

    private fun passosDaSemana(): List<Passo> = reuniaoAtual()?.rot?.passos.orEmpty()
    private fun chave(p: Passo) = if (p.cons) "${p.bloco}#c" else p.bloco

    /** Soma ao relatório o tempo gasto no passo atual (chamado ao trocar de parte ou encerrar). */
    private fun registrarPassoAtual(a: Arquivo, fim: Boolean): Arquivo {
        val p = a.presidir ?: return a
        val passo = passosDaSemana().getOrNull(p.passo) ?: return a
        val seg = Math.round(decorrido()).coerceAtLeast(0)
        val rel = a.relatorios[p.sem] ?: Relatorio(p.sem, p.inicioReuniao.takeIf { it > 0 } ?: p.inicioPasso)
        val k = chave(passo)
        val novo = rel.copy(partes = rel.partes + (k to ((rel.partes[k] ?: 0) + seg)),
            fim = if (fim) System.currentTimeMillis() else rel.fim)
        return a.copy(relatorios = a.relatorios + (p.sem to novo))
    }

    fun pararPresidir() {
        val p = dados.presidir ?: return
        if (!p.ativo) return
        agora = System.currentTimeMillis()
        mudar { a -> registrarPassoAtual(a, fim = true).copy(presidir = p.copy(ativo = false)) }
    }
    fun irPasso(i: Int, total: Int) {
        val p = dados.presidir ?: return
        val n = i.coerceIn(0, (total - 1).coerceAtLeast(0))
        avisado = false
        agora = System.currentTimeMillis()
        mudar { a -> registrarPassoAtual(a, fim = false).copy(presidir = p.copy(passo = n, inicioPasso = System.currentTimeMillis(), pausadoEm = null, acumuladoPausa = 0)) }
        verificarRelogio(n)
    }


    fun relatorio(id: String): Relatorio? = dados.relatorios[id]

    /** Linhas do relatório: cada passo do roteiro com o tempo previsto e o real. */
    data class LinhaRelatorio(val hora: String, val titulo: String, val previstoSeg: Int, val realSeg: Long?)

    fun linhasRelatorio(r: Reuniao): List<LinhaRelatorio> {
        val rel = relatorio(r.id) ?: return emptyList()
        val emCurso = presidindo && dados.presidir?.sem == r.id
        val atual = if (emCurso) passosDaSemana().getOrNull(dados.presidir!!.passo) else null
        return r.rot.passos.map { p ->
            var real = rel.partes[chave(p)]
            if (atual != null && atual == p) real = (real ?: 0) + Math.round(decorrido())
            LinhaRelatorio(Roteiro.hm(p.ini), p.titulo, p.dur * 60, real)
        }
    }

    fun textoRelatorio(r: Reuniao): String {
        val s = tx
        val rel = relatorio(r.id) ?: return ""
        val fmtH = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        fun mmss(seg: Long): String { val a = Math.abs(seg); return (if (seg < 0) "-" else "") + "${a / 60}:" + (a % 60).toString().padStart(2, '0') }
        val sb = StringBuilder()
        sb.append("${s["relatorio"]} — ${r.titulo} · ${r.periodo}\n")
        sb.append("${s["rel_inicio"]}: ${fmtH.format(java.util.Date(rel.inicio))}")
        rel.fim?.let { sb.append(" · ${s["rel_fim"]}: ${fmtH.format(java.util.Date(it))} · ${s["rel_duracao"]}: ${mmss((it - rel.inicio) / 1000)} (${r.rot.total / 60}:${(r.rot.total % 60).toString().padStart(2, '0')}:00)") }
        sb.append("\n\n")
        for (l in linhasRelatorio(r)) {
            val real = l.realSeg
            sb.append("${l.hora}  ${l.titulo}: ")
            if (real == null) sb.append("— (${l.previstoSeg / 60} min)\n")
            else {
                val dif = real - l.previstoSeg
                sb.append("${mmss(real)} / ${l.previstoSeg / 60}:00")
                if (l.previstoSeg > 0) sb.append("  (${if (dif > 0) "+" else ""}${mmss(dif)})")
                sb.append("\n")
            }
        }
        return sb.toString()
    }
    fun pausar() {
        val p = dados.presidir ?: return
        val t = System.currentTimeMillis()
        val novo = if (p.pausadoEm != null) p.copy(acumuladoPausa = p.acumuladoPausa + (t - p.pausadoEm), pausadoEm = null) else p.copy(pausadoEm = t)
        mudar { it.copy(presidir = novo) }
    }
    /** Segundos decorridos no passo atual. */
    fun decorrido(): Double {
        val p = dados.presidir ?: return 0.0
        val fimRef = p.pausadoEm ?: agora
        return (fimRef - p.inicioPasso - p.acumuladoPausa) / 1000.0
    }
    /** Segundos desde o início da reunião (não para com a pausa da parte). */
    fun decorridoReuniao(): Double {
        val p = dados.presidir ?: return 0.0
        val ini = p.inicioReuniao.takeIf { it > 0 } ?: p.inicioPasso
        return (agora - ini) / 1000.0
    }
    /** Chamado a cada atualização do relógio: dispara o aviso quando o tempo acaba. */
    fun verificarFimDoTempo(durMin: Int) {
        if (avisado || durMin <= 0 || dados.presidir?.pausadoEm != null) return
        if (decorrido() >= durMin * 60) {
            avisado = true
            if (cfg.vibrar) Avisos.vibrar(getApplication())
            if (cfg.som) Avisos.bipe()
        }
    }
}
