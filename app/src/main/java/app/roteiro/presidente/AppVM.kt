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
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.EstadoPresidir
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.LeitorApostila
import app.roteiro.presidente.nucleo.LicaoOficial
import app.roteiro.presidente.nucleo.Proxima
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

    var dados by mutableStateOf(repo.carregar())
        private set
    var semanaId by mutableStateOf<String?>(null)
    var agora by mutableLongStateOf(System.currentTimeMillis())
    var ocupado by mutableStateOf<String?>(null)
        private set

    private val canalMsg = Channel<String>(Channel.BUFFERED)
    val mensagens = canalMsg.receiveAsFlow()
    fun avisar(m: String) { canalMsg.trySend(m) }

    val cfg: Config get() = dados.cfg
    val tx: Textos.Idioma get() = Textos.de(cfg.lang)

    init {
        escolherSemanaPadrao()
        // Se o app foi fechado no meio da reunião, volta para a mesma semana no modo Presidir
        dados.presidir?.let { pr ->
            val sem = todasSemanas().firstOrNull { it.id == pr.sem }
            if (pr.ativo && sem != null && System.currentTimeMillis() - pr.inicioPasso < 3 * 3600_000L) {
                if (sem.idioma != cfg.lang) dados = dados.copy(cfg = cfg.copy(lang = sem.idioma))
                semanaId = sem.id
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

    fun irParaSemana(id: String) { if (presidindo) pararPresidir(); semanaId = id }
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

    fun valor(k: String, v: String) { val id = semanaId ?: return; mudarEstado(id) { it.copy(v = it.v + (k to v)) } }
    fun marcar(k: String, on: Boolean) = valor(k, on.toString())
    fun setSC(on: Boolean) { val id = semanaId ?: return; mudarEstado(id) { it.copy(sc = on) } }
    fun setSalas(on: Boolean) { val id = semanaId ?: return; mudarEstado(id) { it.copy(salas = on) } }
    fun limparSemana() { val id = semanaId ?: return; mudar { it.copy(semanas = it.semanas - id) } }

    fun mudarCfg(f: (Config) -> Config) = mudar { it.copy(cfg = f(it.cfg)) }

    /* ---------- cânticos e títulos oficiais ---------- */
    fun tituloCantico(lang: String, n: Int): String? = dados.canticos[lang]?.get(n.toString())
    fun definirCantico(lang: String, n: Int, titulo: String) = mudar { a ->
        val atual = a.canticos[lang].orEmpty()
        val novo = if (titulo.isBlank()) atual - n.toString() else atual + (n.toString() to titulo.trim())
        a.copy(canticos = a.canticos + (lang to novo))
    }
    fun limparCanticos(lang: String) = mudar { it.copy(canticos = it.canticos - lang) }
    fun oficial(pub: String, lang: String, n: Int): LicaoOficial? = dados.oficiais[pub]?.get(lang)?.get(n.toString())

    fun roteiro(sem: Semana): RoteiroMontado = Roteiro.montar(
        sem, estado(sem.id), cfg, licoes, proximaDe(sem),
        tituloCantico = { n -> tituloCantico(sem.idioma, n) },
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
            avisar(msgs.joinToString("\n"))
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
                mudar { a -> a.copy(canticos = a.canticos + (r.idioma to (a.canticos[r.idioma].orEmpty() + r.titulos))) }
                Textos.formatar(s["can_ok"], "n" to r.titulos.size, "i" to Textos.NOME_IDIOMA[r.idioma])
            }
            is Importado.CancioneiroErrado -> Textos.formatar(s["can_antigo"], "t" to r.titulo, "s" to r.simbolo)
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

    /* ---------- cópia de segurança ---------- */
    fun exportarTexto(): String = repo.paraTexto(dados)
    fun importarTexto(t: String): Boolean = try { substituir(repo.deTexto(t)); true } catch (e: Exception) { false }

    /* ---------- modo Presidir ---------- */
    val presidindo: Boolean get() = dados.presidir?.ativo == true && dados.presidir?.sem == semanaId
    val presidir: EstadoPresidir? get() = dados.presidir
    private var avisado = false

    fun iniciarPresidir(totalPassos: Int) {
        val id = semanaId ?: return
        val salvo = dados.presidir
        val novo = if (salvo != null && salvo.sem == id && salvo.passo < totalPassos &&
            System.currentTimeMillis() - salvo.inicioPasso < 3 * 3600_000L) salvo.copy(ativo = true)
        else EstadoPresidir(id, 0, System.currentTimeMillis(), null, 0, true)
        avisado = false
        mudar { it.copy(presidir = novo) }
    }
    fun pararPresidir() { dados.presidir?.let { p -> mudar { it.copy(presidir = p.copy(ativo = false)) } } }
    fun irPasso(i: Int, total: Int) {
        val p = dados.presidir ?: return
        val n = i.coerceIn(0, (total - 1).coerceAtLeast(0))
        avisado = false
        mudar { it.copy(presidir = p.copy(passo = n, inicioPasso = System.currentTimeMillis(), pausadoEm = null, acumuladoPausa = 0)) }
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
