package app.roteiro.presidente.aovivo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.roteiro.presidente.MainActivity
import app.roteiro.presidente.R
import app.roteiro.presidente.dados.Avisos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mantém a notificação com o cronômetro enquanto o modo Presidir está ativo, mesmo com o app em segundo plano.
 * O cronômetro da notificação é contado pelo próprio Android (não precisa atualizar a cada segundo);
 * ela só é refeita quando muda a parte, a pausa, ou quando o tempo da parte acaba.
 * No Android 16 (com Live Updates) o tempo também aparece como um chip na barra de status.
 */
class ServicoPresidir : Service() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var aoAcabar: Runnable? = null
    private var emPrimeiroPlano = false

    override fun onBind(intent: Intent?): IBinder? = null

    private var coleta: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService exige startForeground logo em seguida, mesmo que seja para encerrar
        mostrar(AoVivo.estado.value)
        if (coleta == null) coleta = escopo.launch {
            AoVivo.estado.collect { info ->
                if (info == null || !info.notificacao) encerrar()
                else { mostrar(info); agendarFim(info) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        aoAcabar?.let { handler.removeCallbacks(it) }
        escopo.cancel()
        super.onDestroy()
    }

    private fun encerrar() {
        aoAcabar?.let { handler.removeCallbacks(it) }
        aoAcabar = null
        if (emPrimeiroPlano) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        emPrimeiroPlano = false
        stopSelf()
    }

    private fun mostrar(info: AoVivo.Info?) {
        criarCanal(info?.tCanal ?: getString(R.string.app_name))
        val n = construir(this, info)
        if (!emPrimeiroPlano) {
            val tipo = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            try {
                ServiceCompat.startForeground(this, ID, n, tipo)
                emPrimeiroPlano = true
            } catch (e: RuntimeException) {
                stopSelf()
            }
        } else {
            val pode = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (pode) try { NotificationManagerCompat.from(this).notify(ID, n) } catch (_: SecurityException) { }
        }
    }

    /** Quando o tempo da parte acaba: aviso (se a tela do app não avisou) e notificação com "tempo excedido". */
    private fun agendarFim(info: AoVivo.Info) {
        aoAcabar?.let { handler.removeCallbacks(it) }
        aoAcabar = null
        if (info.pausado || info.durSeg <= 0) return
        val espera = info.fimMs - System.currentTimeMillis()
        if (espera <= 0) return
        val r = Runnable {
            if ((info.som || info.vibrar) && AoVivo.marcarAviso(info.inicioPasso)) {
                if (info.vibrar) Avisos.vibrar(this)
                if (info.som) Avisos.bipe()
            }
            if (AoVivo.estado.value == info) mostrar(info)
        }
        aoAcabar = r
        handler.postDelayed(r, espera + 300)
    }

    private fun criarCanal(nome: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val canal = NotificationChannel(CANAL, nome, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    companion object {
        const val CANAL = "presidir"
        const val ID = 4107

        fun iniciar(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ServicoPresidir::class.java))
            } catch (_: RuntimeException) {
                // Android 12+: não pode começar com o app em segundo plano; tenta de novo quando o app voltar
            }
        }

        private fun mmss(seg: Long): String {
            val a = Math.abs(seg)
            return (if (seg < 0) "+" else "") + "${a / 60}:" + (a % 60).toString().padStart(2, '0')
        }

        fun construir(ctx: Context, info: AoVivo.Info?): Notification {
            val abrir = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_relogio)
                .setContentIntent(abrir)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setColor(0xFF1F3B4D.toInt())
            if (info == null) return b.setContentTitle(ctx.getString(R.string.app_name)).build()

            val agora = System.currentTimeMillis()
            val restaSeg = (info.durSeg * 1000L - info.decorridoMs(agora)) / 1000
            b.setContentTitle(info.titulo).setSubText(info.nomeApp)
            val extras = Bundle()
            // Android 16: pede para virar "Live Update" (chip na barra de status e no topo da tela de bloqueio)
            extras.putBoolean("android.requestPromotedOngoing", true)
            when {
                info.pausado -> {
                    b.setUsesChronometer(false).setShowWhen(false)
                        .setContentText("${info.tPausado} · ${if (info.durSeg > 0) mmss(restaSeg) else mmss(-info.decorridoMs(agora) / 1000)}")
                    extras.putString("android.shortCriticalText", if (info.durSeg > 0) mmss(restaSeg) else info.tPausado)
                }
                info.durSeg > 0 -> {
                    b.setWhen(info.fimMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                        .setContentText(if (restaSeg < 0) info.tExcedido else info.proximo?.let { "${info.tASeguir}: $it" } ?: "")
                }
                else -> {
                    b.setWhen(info.inicioPasso + info.acumuladoPausa).setShowWhen(true).setUsesChronometer(true)
                        .setContentText(info.proximo?.let { "${info.tASeguir}: $it" } ?: "")
                }
            }
            b.addExtras(extras)

            if (info.comControles) {
                fun acao(acao: String, cod: Int) = PendingIntent.getBroadcast(
                    ctx, cod, Intent(acao).setClass(ctx, ReceptorComandos::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.addAction(R.drawable.ic_anterior, info.tVoltar, acao(AoVivo.VOLTAR, 1))
                if (info.pausado) b.addAction(R.drawable.ic_play, info.tContinuar, acao(AoVivo.PAUSAR, 2))
                else b.addAction(R.drawable.ic_pausa, info.tPausar, acao(AoVivo.PAUSAR, 2))
                b.addAction(R.drawable.ic_proximo, info.tAvancar, acao(AoVivo.AVANCAR, 3))
            }
            return b.build()
        }
    }
}
