package app.roteiro.presidente

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.roteiro.presidente.aovivo.AoVivo
import app.roteiro.presidente.aovivo.JanelaControles
import app.roteiro.presidente.aovivo.ReceptorComandos
import app.roteiro.presidente.aovivo.ServicoPresidir
import app.roteiro.presidente.dados.Migracao
import app.roteiro.presidente.ui.TelaGuia
import app.roteiro.presidente.ui.TelaMesa
import app.roteiro.presidente.ui.TelaPip
import app.roteiro.presidente.ui.TelaPrincipal
import app.roteiro.presidente.ui.TemaApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppVM by viewModels()

    /** O app está na janela flutuante (picture-in-picture). */
    private var emPip by mutableStateOf(false)
    /** Topo da dobra (px) quando o dobrável está meio aberto na horizontal ("modo mesa"); null nos outros casos. */
    private var dobraMesaPx by mutableStateOf<Int?>(null)

    private var servicoPedido = false
    private var pediuNotificacao = false
    private val pedirNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Primeira abertura depois de atualizar da versão 1 (HTML): recupera nomes, anotações e apostilas.
        if (savedInstanceState == null && !vm.repo.existe()) {
            val mig = Migracao(this, vm.repo.json)
            if (mig.temVersaoAnterior()) {
                mig.executar { a ->
                    if (a != null) { vm.substituir(a); vm.avisar(vm.tx["migrado"]) }
                    else vm.gravarAgora()
                }
            } else vm.gravarAgora()
        }

        addOnPictureInPictureModeChangedListener { emPip = it.isInPictureInPictureMode }

        // Janela flutuante e notificação acompanham o modo Presidir
        lifecycleScope.launch { AoVivo.estado.collect { aoMudarAoVivo(it) } }

        // Dobráveis: detecta a postura "mesa" (meio aberto, dobra na horizontal)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    val dobra = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    dobraMesaPx = dobra?.takeIf {
                        it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
                    }?.bounds?.top
                }
            }
        }

        setContent {
            TemaApp(vm.cfg.tema, vm.cfg.fonte) {
                val dobra = dobraMesaPx
                // "Ver roteiro completo" no modo mesa vale até a próxima mudança de postura
                var verRoteiro by remember(dobra) { mutableStateOf(false) }
                when {
                    emPip -> TelaPip(vm)
                    dobra != null && vm.presidindo && !verRoteiro -> TelaMesa(vm, dobra) { verRoteiro = true }
                    else -> {
                        TelaPrincipal(vm)
                        // apresentação (ao abrir), novidades da versão ou "Ver apresentação" da Ajuda
                        vm.guia?.let { TelaGuia(vm, it) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // de volta ao app: a janela com os botões não é mais necessária
        JanelaControles.esconder()
        // Se o serviço não pôde começar com o app em segundo plano, começa agora
        if (AoVivo.estado.value?.notificacao == true) ServicoPresidir.iniciar(this)
    }

    override fun onResume() {
        super.onResume()
        // a permissão "Sobrepor a outros apps" pode ter mudado: decide de novo entre a janela com botões e o picture-in-picture
        atualizarPip(AoVivo.estado.value)
    }

    override fun onStop() {
        super.onStop()
        // saiu do app durante a reunião: mostra a janela com os botões sempre à vista (se permitida)
        val info = AoVivo.estado.value
        if (info != null && usaControles(info) && !isChangingConfigurations && !isInPictureInPictureMode) JanelaControles.mostrar(this)
        vm.gravarAgora()
        vm.copiarAgora()   // cópia automática na pasta escolhida (se configurada)
    }

    /* ---------- janela flutuante (picture-in-picture) ---------- */

    private fun aoMudarAoVivo(info: AoVivo.Info?) {
        if (info == null) JanelaControles.esconder()
        atualizarPip(info)
        val notificar = info != null && info.notificacao
        if (notificar && !servicoPedido) {
            if (Build.VERSION.SDK_INT >= 33 && !pediuNotificacao &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pediuNotificacao = true
                pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ServicoPresidir.iniciar(this)
        }
        servicoPedido = notificar
    }

    fun suportaPip(): Boolean =
        Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** A janela flutuante será a de botões sempre à vista (ligada nas Configurações e com a permissão dada). */
    fun usaControles(info: AoVivo.Info?): Boolean =
        info != null && info.pip && info.janelaControles && JanelaControles.permitido(this)

    /** Há alguma janela flutuante neste aparelho (a de botões ou o picture-in-picture). */
    fun temJanela(): Boolean = suportaPip() || JanelaControles.permitido(this)

    @RequiresApi(26)
    private fun parametrosPip(info: AoVivo.Info?): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(if (info != null) acoesPip(info) else emptyList())
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12+: entra sozinho na janela ao ir para a tela inicial ou outro app
            b.setAutoEnterEnabled(info != null && info.pip && !usaControles(info))
            b.setSeamlessResizeEnabled(false)   // conteúdo de texto, não vídeo
        }
        return b.build()
    }

    @RequiresApi(26)
    private fun acoesPip(info: AoVivo.Info): List<RemoteAction> {
        fun acao(icone: Int, titulo: String, acao: String, cod: Int) = RemoteAction(
            Icon.createWithResource(this, icone), titulo, titulo,
            PendingIntent.getBroadcast(this, 10 + cod, Intent(acao).setClass(this, ReceptorComandos::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val pausa = if (info.pausado) acao(R.drawable.ic_play, info.tContinuar, AoVivo.PAUSAR, 2)
        else acao(R.drawable.ic_pausa, info.tPausar, AoVivo.PAUSAR, 2)
        val todas = listOf(acao(R.drawable.ic_anterior, info.tVoltar, AoVivo.VOLTAR, 1), pausa,
            acao(R.drawable.ic_proximo, info.tAvancar, AoVivo.AVANCAR, 3))
        val max = try { maxNumPictureInPictureActions } catch (_: RuntimeException) { 3 }
        return if (max >= 3) todas else todas.drop(1).take(max.coerceAtLeast(0))
    }

    private fun atualizarPip(info: AoVivo.Info?) {
        if (Build.VERSION.SDK_INT < 26 || !suportaPip()) return
        try { setPictureInPictureParams(parametrosPip(info)) } catch (_: RuntimeException) { }
    }

    /** Abre a janela flutuante (botão na barra do modo Presidir, ou ao sair do app no Android 8 a 11). */
    fun entrarPip(manual: Boolean = true): Boolean {
        val info = AoVivo.estado.value ?: return false
        if (!manual && !info.pip) return false
        if (info.janelaControles && JanelaControles.permitido(this)) {
            // janela com botões: ao sair do app ela aparece no onStop; pelo botão, aparece já e o app vai para trás
            if (!manual) return false
            JanelaControles.mostrar(this, manual = true)
            moveTaskToBack(true)
            return true
        }
        if (Build.VERSION.SDK_INT < 26 || !suportaPip()) return false
        return try { enterPictureInPictureMode(parametrosPip(info)) } catch (_: RuntimeException) { false }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // No Android 12+ isso é feito pelo setAutoEnterEnabled
        if (Build.VERSION.SDK_INT in 26..30) entrarPip(manual = false)
    }
}

/** A atividade principal a partir de um Context do Compose (para o botão da janela flutuante). */
fun Context.atividadePrincipal(): MainActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is MainActivity) return c
        c = c.baseContext
    }
    return null
}
