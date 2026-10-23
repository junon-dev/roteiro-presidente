@file:OptIn(ExperimentalLayoutApi::class)

package app.roteiro.presidente.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roteiro.presidente.nucleo.Bloco
import app.roteiro.presidente.nucleo.Campo
import app.roteiro.presidente.nucleo.Conselho
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.Estilo
import app.roteiro.presidente.nucleo.Seg
import app.roteiro.presidente.nucleo.Textos

/** Ações de edição usadas pelos blocos. */
class Edicao(
    val estado: EstadoSemana,
    val valor: (String, String) -> Unit,
    val marcar: (String, Boolean) -> Unit,
)

private fun Modifier.barraEsquerda(cor: Color, larg: Dp = 4.dp) = drawBehind {
    drawRect(cor, topLeft = Offset.Zero, size = Size(larg.toPx(), size.height))
}

@Composable
fun Fala(segs: List<Seg>, e: EstadoSemana) {
    val c = LocalCores.current
    val texto = buildAnnotatedString {
        for (s in segs) when (s) {
            is Seg.Txt -> append(s.s)
            is Seg.Nome -> {
                val v = e.valor(s.k)
                if (v.isNotBlank()) {
                    val ini = length; append(v); addStyle(SpanStyle(fontWeight = FontWeight.Bold), ini, length)
                } else {
                    val ini = length; append("________"); addStyle(SpanStyle(color = c.suave), ini, length)
                }
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(c.falaFundo, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .barraEsquerda(c.falaBorda, 3.dp)
            .padding(start = 13.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
    ) { Text(texto, fontSize = 19.sp, lineHeight = 28.sp, color = c.tinta) }
}

@Composable
fun CampoNome(campo: Campo, ed: Edicao, largura: Dp = 230.dp) {
    OutlinedTextField(
        value = ed.estado.valor(campo.k),
        onValueChange = { ed.valor(campo.k, it) },
        label = { Text(campo.rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.width(largura),
    )
}

@Composable
fun CampoArea(campo: Campo, ed: Edicao) {
    OutlinedTextField(
        value = ed.estado.valor(campo.k),
        onValueChange = { ed.valor(campo.k, it) },
        label = { Text(campo.rotulo) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
fun Instrucao(t: String) {
    val c = LocalCores.current
    Text(marcado(t), color = c.suave, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
fun CartaoBloco(
    b: Bloco,
    ed: Edicao,
    lang: String,
    atual: Boolean,
    consAtual: Boolean,
    feito: Boolean,
    aoTocar: ((cons: Boolean) -> Unit)?,
) {
    val c = LocalCores.current
    val s = Textos.de(lang)
    val fundo = when (b.estilo) { Estilo.SALA -> c.salaFundo; Estilo.SC -> c.scFundo; else -> c.cartao }
    val corTit = when (b.estilo) { Estilo.SALA -> c.sala; Estilo.SC -> c.sc; else -> c.tinta }
    val corBarra = when (b.estilo) { Estilo.SALA -> c.sala; Estilo.SC -> c.sc; else -> corSecao(c, b.sec) }
    val forma = RoundedCornerShape(12.dp)
    var mod = Modifier.fillMaxWidth().padding(vertical = 5.dp)
        .background(fundo, forma)
        .border(if (atual) 3.dp else 1.dp, if (atual) c.falaBorda else c.linha, forma)
    if (aoTocar != null) mod = mod.clickable { aoTocar(false) }
    Row(mod.alpha(if (feito) 0.6f else 1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(b.hora, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.tinta, modifier = Modifier.width(58.dp))
        Column(Modifier.weight(1f).barraEsquerda(corBarra).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.titulo, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = corTit)
                        b.tag?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(it.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ministerio,
                                modifier = Modifier.border(1.5.dp, c.ministerio, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp))
                        }
                    }
                    b.subtitulo?.let { Text(it, fontStyle = FontStyle.Italic, color = c.suave, fontSize = 16.sp) }
                }
                if (b.durTxt.isNotEmpty()) Text(b.durTxt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.suave,
                    modifier = Modifier.background(c.campo, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp))
            }
            if (!b.falaNoFim) b.falas.forEach { Fala(it, ed.estado) }
            b.instr.forEach { Instrucao(it) }
            if (b.lista.isNotEmpty()) Column(Modifier.padding(vertical = 4.dp)) {
                b.lista.forEach { item ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", fontSize = 16.sp, color = c.tinta)
                        Text(marcado(item), fontSize = 16.sp, lineHeight = 23.sp, color = c.tinta)
                    }
                }
            }
            if (b.campos.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)) { b.campos.forEach { CampoNome(it, ed) } }
            if (b.salaB.isNotEmpty()) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(c.salaFundo, RoundedCornerShape(8.dp))
                    .border(1.5.dp, c.sala, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(s["sala_b"] + ":", color = c.sala, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                b.salaB.forEach { CampoNome(it, ed, 200.dp) }
            }
            b.areas.forEach { CampoArea(it, ed) }
            if (b.falaNoFim) b.falas.forEach { Fala(it, ed.estado) }
            b.conselho?.let { CartaoConselho(it, ed, lang, consAtual, aoTocar?.let { f -> { f(true) } }) }
            b.termino?.let {
                Text(buildAnnotatedString { append(s["termino"] + ": "); val i = length; append(it); addStyle(SpanStyle(fontWeight = FontWeight.Bold), i, length); append(" · 1h45") },
                    color = c.suave, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun CartaoConselho(k: Conselho, ed: Edicao, lang: String, atual: Boolean, aoTocar: (() -> Unit)?) {
    val c = LocalCores.current
    val s = Textos.de(lang)
    val forma = RoundedCornerShape(10.dp)
    var mod = Modifier.fillMaxWidth().padding(top = 10.dp)
        .background(c.consFundo, forma)
        .border(if (atual) 3.dp else 1.dp, if (atual) c.ministerio else c.consBorda, forma)
    if (aoTocar != null) mod = mod.clickable { aoTocar() }
    Column(mod.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(buildAnnotatedString {
            append(s["cons_t"].uppercase()); val i = length; append(" · " + s["cons_min"])
            addStyle(SpanStyle(fontWeight = FontWeight.Medium), i, length)
        }, color = c.ministerio, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.8.sp)
        if (k.licao != null) Text(buildAnnotatedString {
            append(k.licao)
            k.licaoTitulo?.let { append(" — $it") }
            k.secaoLmd?.takeIf { it.isNotBlank() }?.let { val i = length; append(" · $it"); addStyle(SpanStyle(fontWeight = FontWeight.Normal, color = c.suave), i, length) }
        }, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.tinta, modifier = Modifier.padding(top = 4.dp))
        k.objetivo?.let { Text(it, color = c.suave, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        k.aprendemos?.let { Text(marcado("<b>${s["aprendemos"]}:</b> $it"), color = c.suave, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        k.pontos.forEach { p ->
            val dest = p.destaque
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .background(if (dest) c.ministerio.copy(alpha = 0.13f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(end = 6.dp),
            ) {
                if (p.k != null) Checkbox(
                    checked = ed.estado.marcado(p.k), onCheckedChange = { on -> ed.marcar(p.k, on) },
                    colors = CheckboxDefaults.colors(checkedColor = c.ok),
                )
                Text(buildAnnotatedString {
                    p.titulo?.let { t -> val i = length; append("$t "); addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (dest) c.ministerio else c.tinta), i, length) }
                    append(p.texto)
                }, fontSize = 16.sp, lineHeight = 22.sp, color = c.tinta, modifier = Modifier.padding(top = 12.dp).weight(1f))
            }
        }
        if (k.outros.isNotEmpty()) {
            var aberto by rememberSaveable(k.notaK) { mutableStateOf(false) }
            Text((if (aberto) "▾ " else "▸ ") + s["outros"], color = c.suave, fontSize = 14.sp,
                modifier = Modifier.clickable { aberto = !aberto }.padding(vertical = 6.dp))
            AnimatedVisibility(aberto) {
                Column {
                    k.outros.forEach { p ->
                        Text(buildAnnotatedString {
                            p.titulo?.let { t -> val i = length; append("$t "); addStyle(SpanStyle(fontWeight = FontWeight.Bold), i, length) }
                            append(p.texto)
                        }, fontSize = 14.sp, color = c.suave, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                    }
                }
            }
        }
        k.observacao?.let { Text(it, color = c.suave, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        Text(marcado(s["cons_passos"]), color = c.suave, fontSize = 14.5.sp, modifier = Modifier.padding(top = 6.dp))
        CampoArea(Campo(k.notaK, s["f_notas"], true), ed)
        Text(s["cons_obs"], color = c.suave, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun TituloSecao(sec: String, titulo: String) {
    val c = LocalCores.current
    Text(titulo.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
            .background(corSecao(c, sec), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
fun Dica(texto: String, sc: Boolean) {
    val c = LocalCores.current
    if (sc) Text(texto, color = c.sc, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, top = 4.dp, bottom = 4.dp)
            .background(c.scFundo, RoundedCornerShape(8.dp)).padding(10.dp))
    else Text(texto, color = c.suave, fontSize = 14.sp, fontStyle = FontStyle.Italic,
        modifier = Modifier.padding(start = 72.dp, top = 2.dp, bottom = 2.dp))
}

@Composable
fun Pilula(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(cor, RoundedCornerShape(50)))
}
