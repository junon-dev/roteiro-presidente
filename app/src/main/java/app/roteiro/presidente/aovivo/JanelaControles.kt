package app.roteiro.presidente.aovivo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.roteiro.presidente.MainActivity
import app.roteiro.presidente.R
import app.roteiro.presidente.dados.Avisos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Janela flutuante com os botões sempre à vista (permissão "Sobrepor a outros apps").
 *
 * O picture-in-picture do Android não aceita botões próprios dentro da janela: os botões dele só aparecem
 * depois de um toque e somem em seguida. Por isso, quando a permissão foi dada, o app usa esta janela
 * sobreposta, que mostra a parte, o cronômetro e os botões voltar/pausar/avançar o tempo todo.
 * Sem a permissão, continua usando o picture-in-picture (com os botões do Android).
 *
 * Boas práticas seguidas: a janela não recebe foco (o teclado e os toques fora dela continuam no outro app),
 * pode ser arrastada e fica sempre dentro da tela, pode ser recolhida num botão pequeno, lembra a posição,
 * some sozinha quando o app volta para a frente ou a reunião termina, e os botões têm pelo menos 48 dp.
 */
object JanelaControles {

    private var vista: ComposeView? = null
    private var dono: DonoJanela? = null
    private var lp: WindowManager.LayoutParams? = null
    private var app: Context? = null
    private var callbacks: ComponentCallbacks? = null
    private var observador: Job? = null
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** A permissão "Sobrepor a outros apps" foi dada. */
    fun permitido(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Abre a tela do Android onde a pessoa permite "Sobrepor a outros apps" para este app. */
    fun pedirPermissao(ctx: Context) {
        val nova = if (ctx is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        val pacote = Uri.parse("package:${ctx.packageName}")
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pacote).addFlags(nova))
        } catch (_: ActivityNotFoundException) {
            // alguns aparelhos (Android Go) não têm essa tela: abre os detalhes do app
            try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacote).addFlags(nova)) }
            catch (_: RuntimeException) { }
        } catch (_: SecurityException) { }
    }

    val visivel: Boolean get() = vista != null

    /** Aberta pelo botão da barra do modo Presidir (vale mesmo com a janela automática desligada). */
    private var manualAtivo = false

    fun mostrar(ctx: Context, manual: Boolean = false) {
        if (vista != null) return
        val a = ctx.applicationContext
        if (!permitido(a)) return
        val info = AoVivo.estado.value ?: return
        if (!info.janelaControles || !(info.pip || manual)) return
        manualAtivo = manual
        val wm = a.getSystemService(WindowManager::class.java) ?: return
        val dens = a.resources.displayMetrics.density
        val prefs = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // sem foco: o teclado e os toques fora da janela continuam no app de baixo
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", (12 * dens).toInt())
            y = prefs.getInt("y", (90 * dens).toInt())
            windowAnimations = android.R.style.Animation_Dialog
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val d = DonoJanela().also { it.criar() }
        val cv = ComposeView(a).apply {
            setViewTreeLifecycleOwner(d)
            setViewTreeSavedStateRegistryOwner(d)
            setViewTreeViewModelStoreOwner(d)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { JanelaAoVivo(a) }
        }
        try {
            wm.addView(cv, params)
        } catch (_: RuntimeException) {
            d.destruir()
            return
        }
        vista = cv; dono = d; lp = params; app = a
        d.resumir()
        // ao girar a tela ou abrir/fechar o dobrável, traz a janela de volta para dentro da tela
        val cb = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) { cv.post { mover(0f, 0f) } }
            @Deprecated("Deprecated in Java") override fun onLowMemory() {}
        }
        a.registerComponentCallbacks(cb)
        callbacks = cb
        cv.post { mover(0f, 0f) }
        // a reunião terminou ou a janela foi desligada nas Configurações: fecha
        observador = escopo.launch {
            AoVivo.estado.collect { i -> if (i == null || !i.janelaControles || !(i.pip || manualAtivo)) esconder() }
        }
    }

    fun esconder() {
        val cv = vista ?: return
        val a = app
        observador?.cancel(); observador = null
        lp?.let { p -> a?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("x", p.x)?.putInt("y", p.y)?.apply() }
        callbacks?.let { a?.unregisterComponentCallbacks(it) }
        callbacks = null
        try { a?.getSystemService(WindowManager::class.java)?.removeViewImmediate(cv) } catch (_: RuntimeException) { }
        dono?.destruir()
        vista = null; dono = null; lp = null
    }

    private var restoX = 0f
    private var restoY = 0f

    /** Arrasta a janela (dx, dy em px) e mantém tudo dentro da tela. */
    private fun mover(dx: Float, dy: Float) {
        val cv = vista ?: return
        val p = lp ?: return
        val a = app ?: return
        val wm = a.getSystemService(WindowManager::class.java) ?: return
        val (larg, alt) = if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else a.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        val margem = (4 * a.resources.displayMetrics.density).toInt()
        // guarda as frações de pixel para o arraste lento não "travar"
        val tx = dx + restoX; val ty = dy + restoY
        restoX = tx - tx.toInt(); restoY = ty - ty.toInt()
        p.x = (p.x + tx.toInt()).coerceIn(margem, (larg - cv.width - margem).coerceAtLeast(margem))
        p.y = (p.y + ty.toInt()).coerceIn(margem, (alt - cv.height - margem).coerceAtLeast(margem))
        try { wm.updateViewLayout(cv, p) } catch (_: RuntimeException) { }
    }

    private fun salvarPosicao() {
        val p = lp ?: return
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt("x", p.x)?.putInt("y", p.y)?.apply()
    }

    private fun abrirApp(ctx: Context) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        try { ctx.startActivity(i) } catch (_: RuntimeException) { }
    }

    private fun recolhidaSalva(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("recolhida", false)
    private fun salvarRecolhida(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("recolhida", v).apply()

    private const val PREFS = "janela_controles"

    /** Conteúdo da janela, ligado ao estado do modo Presidir. */
    @Composable
    private fun JanelaAoVivo(ctx: Context) {
        val info by AoVivo.estado.collectAsState()
        var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var recolhida by remember { mutableStateOf(recolhidaSalva(ctx)) }
        LaunchedEffect(Unit) {
            while (true) {
                agora = System.currentTimeMillis()
                // fim do tempo da parte: avisa (o serviço da notificação também avisa; só um dos dois toca)
                AoVivo.estado.value?.let { i ->
                    if (!i.pausado && i.durSeg > 0 && agora >= i.fimMs && (i.som || i.vibrar) && AoVivo.marcarAviso(i.inicioPasso)) {
                        if (i.vibrar) Avisos.vibrar(ctx)
                        if (i.som) Avisos.bipe()
                    }
                }
                delay(250)
            }
        }
        val i = info ?: return
        val arrastar = Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = { salvarPosicao() }) { mudanca, d -> mudanca.consume(); mover(d.x, d.y) }
        }
        val dens = LocalDensity.current
        // o tamanho da fonte do sistema vale, mas sem passar de 1,3 (a janela é pequena)
        CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale.coerceAtMost(1.3f))) {
            ControlesFlutuantes(
                i, agora, recolhida, arrastar,
                aoComando = { c -> AoVivo.comandos.tryEmit(c) },
                aoRecolher = { v -> recolhida = v; salvarRecolhida(ctx, v); vista?.post { mover(0f, 0f) } },
                aoAbrirApp = { abrirApp(ctx) },
            )
        }
    }

    /** Dono do ciclo de vida da ComposeView (a janela não pertence a nenhuma Activity). */
    private class DonoJanela : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registro = LifecycleRegistry(this)
        private val estadoSalvo = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registro
        override val savedStateRegistry: SavedStateRegistry get() = estadoSalvo.savedStateRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        fun criar() { estadoSalvo.performRestore(null); registro.currentState = Lifecycle.State.CREATED }
        fun resumir() { registro.currentState = Lifecycle.State.RESUMED }
        fun destruir() {
            if (registro.currentState != Lifecycle.State.INITIALIZED) registro.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

/* ---------- aparência (também usada nas imagens de teste) ---------- */

private val Fundo = Color(0xF2111827)
private val FundoExcedido = Color(0xF2450A0A)
private val Botao = Color(0xFF374151)
private val Azul = Color(0xFF2563EB)
private val Verde = Color(0xFF34D399)
private val Amarelo = Color(0xFFFBBF24)
private val VermelhoTxt = Color(0xFFFCA5A5)
private val Suave = Color(0xFF9CA3AF)
private val Claro = Color(0xFFF9FAFB)

private fun corSecao(sec: String?): Color = when (sec) {
    "tesouros", "publica" -> Color(0xFF7DD3FC)
    "ministerio" -> Color(0xFFFCD34D)
    "vida", "celebracao" -> Color(0xFFFCA5A5)
    "sentinela" -> Color(0xFFA5B4FC)
    else -> Suave
}

/**
 * Janela flutuante com controles. Aberta: parte, cronômetro, a seguir e os botões voltar/pausar/avançar.
 * Recolhida: só o cronômetro num botão pequeno (toque para abrir).
 */
@Composable
fun ControlesFlutuantes(
    i: AoVivo.Info,
    agora: Long,
    recolhida: Boolean,
    arrastar: Modifier = Modifier,
    aoComando: (String) -> Unit = {},
    aoRecolher: (Boolean) -> Unit = {},
    aoAbrirApp: () -> Unit = {},
) {
    val decSeg = i.decorridoMs(agora) / 1000
    val restaSeg = i.durSeg - decSeg
    val excedido = i.durSeg > 0 && restaSeg < 0
    val txt = if (i.durSeg <= 0) mmss(decSeg.coerceAtLeast(0)) else (if (restaSeg < 0) "+" else "") + mmss(Math.abs(restaSeg))
    val cor = when {
        i.durSeg <= 0 -> Claro
        excedido -> VermelhoTxt
        restaSeg <= maxOf(60, (i.durSeg * 0.2).toLong()) -> Amarelo
        else -> Verde
    }
    val fracao = if (i.durSeg > 0) (decSeg.toFloat() / i.durSeg).coerceIn(0f, 1f) else 0f
    val fundo = if (excedido) FundoExcedido else Fundo
    val forma = RoundedCornerShape(if (recolhida) 28.dp else 18.dp)

    if (recolhida) {
        Row(
            arrastar.padding(6.dp).shadow(8.dp, forma).background(fundo, forma).border(1.dp, Color(0x33FFFFFF), forma)
                .clickable(onClickLabel = i.tExpandir) { aoRecolher(false) }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(cor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(txt, color = cor.copy(alpha = if (i.pausado) 0.6f else 1f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_abrir_janela), i.tExpandir, tint = Suave, modifier = Modifier.size(20.dp))
        }
        return
    }

    Column(
        arrastar.padding(6.dp).width(300.dp).shadow(10.dp, forma).background(fundo, forma).border(1.dp, Color(0x33FFFFFF), forma)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                i.secNome?.let {
                    Text(it.uppercase(), color = corSecao(i.sec), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(i.titulo, color = Claro, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconeJanela(R.drawable.ic_recolher, i.tRecolher) { aoRecolher(true) }
            IconeJanela(R.drawable.ic_abrir_app, i.tAbrirApp, aoAbrirApp)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 6.dp)) {
            Text(
                when {
                    i.pausado -> i.tPausado
                    excedido -> i.tExcedido
                    i.proximo != null -> "${i.tASeguir}: ${i.proximo}"
                    else -> ""
                },
                color = when { i.pausado -> Amarelo; excedido -> VermelhoTxt; else -> Suave },
                fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (i.pausado || excedido) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f).padding(end = 8.dp, bottom = 6.dp),
            )
            Text(txt, color = cor.copy(alpha = if (i.pausado) 0.6f else 1f), fontSize = 44.sp, lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (i.durSeg > 0) {
            Box(Modifier.padding(end = 6.dp, top = 2.dp).fillMaxWidth().height(5.dp).background(Color(0xFF374151), RoundedCornerShape(5.dp))) {
                Box(Modifier.fillMaxWidth(fracao).fillMaxHeight().background(cor, RoundedCornerShape(5.dp)))
            }
        }
        if (i.comControles) {
            Row(Modifier.padding(top = 10.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoJanela(R.drawable.ic_anterior, i.tVoltar, false, Modifier.weight(1f)) { aoComando(AoVivo.VOLTAR) }
                BotaoJanela(if (i.pausado) R.drawable.ic_play else R.drawable.ic_pausa, if (i.pausado) i.tContinuar else i.tPausar, false,
                    Modifier.weight(1f)) { aoComando(AoVivo.PAUSAR) }
                BotaoJanela(R.drawable.ic_proximo, i.tAvancar, true, Modifier.weight(1f)) { aoComando(AoVivo.AVANCAR) }
            }
        }
    }
}

@Composable
private fun BotaoJanela(icone: Int, descricao: String, destaque: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp)
            .background(if (destaque) Azul else Botao, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = descricao, onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icone), null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun IconeJanela(icone: Int, descricao: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(onClickLabel = descricao, onClick = onClick).semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icone), null, tint = Suave, modifier = Modifier.size(20.dp)) }
}

private fun mmss(seg: Long): String = "${seg / 60}:" + (seg % 60).toString().padStart(2, '0')
