package app.roteiro.presidente.aovivo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ponte entre o modo Presidir (no AppVM) e o que continua visível quando o app vai para segundo plano:
 * a janela flutuante (com botões, ou o picture-in-picture do Android) e a notificação com o cronômetro.
 * Todos os tempos são marcas de relógio (ms desde 1970), então o cronômetro nunca atrasa em segundo plano.
 */
object AoVivo {

    /** O que a janela flutuante e a notificação mostram. Muda só quando muda a parte, a pausa ou os textos. */
    data class Info(
        val nomeApp: String,
        val titulo: String,
        val sec: String?,
        val proximo: String?,
        val durSeg: Int,
        val inicioPasso: Long,
        val acumuladoPausa: Long,
        val pausadoEm: Long?,
        val inicioReuniao: Long,
        val totalMin: Int,
        val passo: Int,
        val totalPassos: Int,
        val som: Boolean,
        val vibrar: Boolean,
        val notificacao: Boolean,
        val pip: Boolean,
        /** false quando a tela do app foi fechada: a notificação fica só com o cronômetro e o toque para abrir */
        val comControles: Boolean = true,
        // textos no idioma do roteiro
        val tPausar: String,
        val tContinuar: String,
        val tAvancar: String,
        val tVoltar: String,
        val tASeguir: String,
        val tPausado: String,
        val tCanal: String,
        val tExcedido: String,
        /** nome da seção no idioma do roteiro (etiqueta colorida na janela) */
        val secNome: String? = null,
        /** janela flutuante com os botões sempre à vista (precisa da permissão "Sobrepor a outros apps") */
        val janelaControles: Boolean = false,
        val tExpandir: String = "",
        val tRecolher: String = "",
        val tAbrirApp: String = "",
    ) {
        val pausado: Boolean get() = pausadoEm != null
        /** Milissegundos decorridos na parte atual (descontando as pausas). */
        fun decorridoMs(agora: Long): Long = (pausadoEm ?: agora) - inicioPasso - acumuladoPausa
        /** Momento em que o tempo da parte acaba (se não houver mais pausas). */
        val fimMs: Long get() = inicioPasso + acumuladoPausa + durSeg * 1000L
    }

    val estado = MutableStateFlow<Info?>(null)

    /** Comandos vindos dos botões da notificação e da janela flutuante. */
    val comandos = MutableSharedFlow<String>(extraBufferCapacity = 8)

    const val PAUSAR = "app.roteiro.presidente.PAUSAR"
    const val AVANCAR = "app.roteiro.presidente.AVANCAR"
    const val VOLTAR = "app.roteiro.presidente.VOLTAR"

    /** Evita aviso duplo de fim de tempo (tela do app e serviço em segundo plano). */
    @Volatile private var avisadoPara: Long = -1L
    @Synchronized fun marcarAviso(chave: Long): Boolean {
        if (avisadoPara == chave) return false
        avisadoPara = chave
        return true
    }
}

/** Recebe os toques nos botões da notificação e da janela flutuante. */
class ReceptorComandos : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val acao = intent.action ?: return
        if (acao in listOf(AoVivo.PAUSAR, AoVivo.AVANCAR, AoVivo.VOLTAR)) AoVivo.comandos.tryEmit(acao)
    }
}
