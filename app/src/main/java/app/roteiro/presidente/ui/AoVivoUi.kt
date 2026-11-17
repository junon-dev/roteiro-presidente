package app.roteiro.presidente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roteiro.presidente.AppVM
import app.roteiro.presidente.R
import app.roteiro.presidente.nucleo.Passo
import app.roteiro.presidente.nucleo.Roteiro
import app.roteiro.presidente.nucleo.Textos
import kotlinx.coroutines.delay

/* Telas usadas quando o app sai da tela cheia durante o modo Presidir:
 * a janela flutuante (picture-in-picture) e o "modo mesa" dos dobráveis meio abertos. */

private val FundoAoVivo = Color(0xFF111827)
private val FundoExcedido = Color(0xFF450A0A)
private val Painel2 = Color(0xFF1F2937)
private val Verde = Color(0xFF34D399)
private val Amarelo = Color(0xFFFBBF24)
private val VermelhoTxt = Color(0xFFFCA5A5)
private val Suave = Color(0xFF9CA3AF)
private val Claro = Color(0xFFF9FAFB)

/** Cronômetro da parte: texto, cor e quanto já passou (0 a 1). */
private class Crono(val txt: String, val cor: Color, val fundo: Color, val fracao: Float, val excedido: Boolean, val pausado: Boolean)

private fun crono(vm: AppVM, p: Passo): Crono {
    val dec = vm.decorrido()
    val total = p.dur * 60.0
    val resta = total - dec
    val pausado = vm.presidir?.pausadoEm != null
    if (p.dur == 0) {
        val v = dec.toLong().coerceAtLeast(0)
        return Crono("${v / 60}:" + (v % 60).toString().padStart(2, '0'), Claro, FundoAoVivo, 0f, false, pausado)
    }
    val v = Math.abs(resta).toLong()
    val txt = (if (resta < 0) "+" else "") + "${v / 60}:" + (v % 60).toString().padStart(2, '0')
    return when {
        resta < 0 -> Crono(txt, VermelhoTxt, FundoExcedido, 1f, true, pausado)
        resta <= maxOf(60.0, total * 0.2) -> Crono(txt, Amarelo, FundoAoVivo, (dec / total).toFloat().coerceIn(0f, 1f), false, pausado)
        else -> Crono(txt, Verde, FundoAoVivo, (dec / total).toFloat().coerceIn(0f, 1f), false, pausado)
    }
}

private fun corDaSecao(sec: String?): Color = when (sec) {
    "tesouros", "publica" -> Color(0xFF7DD3FC)
    "ministerio" -> Color(0xFFFCD34D)
    "vida", "celebracao" -> Color(0xFFFCA5A5)
    "sentinela" -> Color(0xFFA5B4FC)
    else -> Suave
}

/** Atualiza o relógio do modo Presidir enquanto uma destas telas está aberta. */
@Composable
fun RelogioPresidir(vm: AppVM) {
    val presidindo = vm.presidindo
    val idx = vm.presidir?.passo
    LaunchedEffect(presidindo, idx) {
        while (presidindo && vm.relogioAutomatico) {
            vm.agora = System.currentTimeMillis()
            vm.passoAtual()?.let { vm.verificarFimDoTempo(it.dur) }
            delay(250)
        }
    }
    val view = LocalView.current
    val ligada = presidindo && vm.cfg.tela
    DisposableEffect(ligada) {
        view.keepScreenOn = ligada
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun Barra(fracao: Float, cor: Color, altura: Float) {
    Box(Modifier.fillMaxWidth().height(altura.dp).background(Color(0xFF374151), RoundedCornerShape(altura.dp))) {
        Box(Modifier.fillMaxWidth(fracao).fillMaxHeight().background(cor, RoundedCornerShape(altura.dp)))
    }
}

/** Janela flutuante: parte atual, cronômetro grande e a próxima parte. Os botões são os do próprio Android. */
@Composable
fun TelaPip(vm: AppVM) {
    RelogioPresidir(vm)
    val s = vm.tx
    val p = vm.passoAtual()
    val dens = LocalDensity.current
    // o tamanho do texto escolhido nas Configurações não vale aqui: a janela é pequena
    CompositionLocalProvider(LocalDensity provides Density(dens.density, 1f)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(if (p != null) crono(vm, p).fundo else FundoAoVivo)) {
            val esc = minOf(maxHeight.value / 135f, maxWidth.value / 240f).coerceIn(0.75f, 2.5f)
            if (p == null) {
                Text(vm.nomeApp, color = Claro, fontSize = (14 * esc).sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            } else {
                val c = crono(vm, p)
                val sec = vm.secDoPasso(p)
                val prox = vm.proximosPassos(1).firstOrNull()
                Column(Modifier.fillMaxSize().padding(horizontal = (10 * esc).dp, vertical = (7 * esc).dp)) {
                    sec?.let {
                        Text((s.sec[it] ?: "").uppercase(), color = corDaSecao(it), fontSize = (8.5f * esc).sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(p.titulo, color = Claro, fontSize = (13 * esc).sp, fontWeight = FontWeight.Bold, lineHeight = (15 * esc).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f).padding(end = 6.dp)) {
                            when {
                                c.pausado -> Text(s["pausado"].replaceFirstChar { it.uppercase() }, color = Amarelo, fontSize = (10 * esc).sp, fontWeight = FontWeight.Bold)
                                c.excedido -> Text(s["excedido"], color = VermelhoTxt, fontSize = (10 * esc).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                prox != null -> Text("${s["aseguir"]}: ${prox.titulo}", color = Suave, fontSize = (9.5f * esc).sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (11 * esc).sp)
                            }
                        }
                        Text(c.txt, color = c.cor.copy(alpha = if (c.pausado) 0.6f else 1f), fontSize = (38 * esc).sp,
                            fontWeight = FontWeight.ExtraBold, lineHeight = (38 * esc).sp, maxLines = 1)
                    }
                    if (p.dur > 0) {
                        Spacer(Modifier.height((4 * esc).dp))
                        Barra(c.fracao, c.cor, 4 * esc)
                    }
                }
            }
        }
    }
}

/**
 * Dobrável meio aberto na horizontal (Galaxy Flip/Fold apoiado na mesa ou na tribuna):
 * em cima o cronômetro grande, embaixo os botões e o que vem a seguir.
 */
@Composable
fun TelaMesa(vm: AppVM, dobraTopoPx: Int, aoVerRoteiro: () -> Unit) {
    RelogioPresidir(vm)
    val s = vm.tx
    val p = vm.passoAtual() ?: return
    val c = crono(vm, p)
    val dens = LocalDensity.current
    val topo = with(dens) { dobraTopoPx.toDp() }
    val sec = vm.secDoPasso(p)
    val reu = vm.reuniaoAtual()
    val reuniao = vm.decorridoReuniao().coerceAtLeast(0.0).toLong()
    val txtReuniao = "${reuniao / 3600}:" + ((reuniao / 60) % 60).toString().padStart(2, '0') + ":" + (reuniao % 60).toString().padStart(2, '0')
    val faltam = Math.round((reu?.rot?.total ?: 105) - reuniao / 60.0).toInt()
    val txtFaltam = if (faltam >= 0) Textos.formatar(s["faltam"], "n" to faltam) else "+${-faltam} min"

    Column(Modifier.fillMaxSize().background(Painel2)) {
        Column(
            Modifier.fillMaxWidth().height(topo).background(c.fundo).statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            sec?.let {
                Text((s.sec[it] ?: "").uppercase(), color = corDaSecao(it), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(p.titulo, color = Claro, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val tam = (topo.value * 0.28f).coerceIn(48f, 140f)
            Text(c.txt, color = c.cor.copy(alpha = if (c.pausado) 0.6f else 1f), fontSize = tam.sp, lineHeight = tam.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                when {
                    c.pausado -> s["pausado"].replaceFirstChar { it.uppercase() }
                    c.excedido -> s["excedido"]
                    p.dur > 0 -> Textos.formatar(s["restamDe"], "t" to "${p.dur}:00")
                    else -> ""
                },
                color = if (c.excedido) VermelhoTxt else Suave, fontSize = 14.sp,
            )
            if (p.dur > 0) { Spacer(Modifier.height(8.dp)); Barra(c.fracao, c.cor, 7f) }
            Spacer(Modifier.height(6.dp))
            Text("${s["reuniao"]} $txtReuniao · $txtFaltam", color = Suave, fontSize = 13.sp)
        }
        Column(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotaoMesa(R.drawable.ic_anterior, s["passoAnt"], false, Modifier.weight(1f)) { vm.avancar(-1) }
                BotaoMesa(if (c.pausado) R.drawable.ic_play else R.drawable.ic_pausa, if (c.pausado) s["continuar"] else s["pausar"], false, Modifier.weight(1f)) { vm.pausar() }
                BotaoMesa(R.drawable.ic_proximo, s["proxPasso"], true, Modifier.weight(1f)) { vm.avancar(1) }
            }
            val prox = vm.proximosPassos(3)
            if (prox.isNotEmpty()) {
                Text(s["aseguir"].uppercase(), color = Suave, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                prox.forEach { q ->
                    Row(Modifier.fillMaxWidth().background(FundoAoVivo, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(q.titulo, color = Claro, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${Roteiro.hm(q.ini)} · ${q.dur} min", color = Suave, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = aoVerRoteiro, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(s["mesaRoteiro"], color = Color(0xFF93C5FD))
            }
        }
    }
}

@Composable
private fun BotaoMesa(icone: Int, descricao: String, destaque: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(64.dp)
            .background(if (destaque) Color(0xFF2563EB) else Color(0xFF374151), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icone), null, tint = Color.White, modifier = Modifier.size(30.dp)) }
}
