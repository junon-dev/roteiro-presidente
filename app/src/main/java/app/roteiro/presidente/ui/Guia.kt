package app.roteiro.presidente.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.roteiro.presidente.AppVM
import app.roteiro.presidente.BuildConfig
import app.roteiro.presidente.Produto
import app.roteiro.presidente.R
import app.roteiro.presidente.aovivo.JanelaControles
import app.roteiro.presidente.nucleo.Dicas
import app.roteiro.presidente.nucleo.Textos
import kotlinx.coroutines.launch

/* Apresentação ao abrir o app, novidades da versão e o painel "Ajuda e dicas". */

fun iconeDica(nome: String): Int = when (nome) {
    "livro" -> R.drawable.ic_livro
    "editar" -> R.drawable.ic_editar
    "play" -> R.drawable.ic_play
    "janela" -> R.drawable.ic_janela
    "arquivo" -> R.drawable.ic_arquivo
    "relogio" -> R.drawable.ic_relogio
    "palco" -> R.drawable.ic_palco
    "ajuda" -> R.drawable.ic_ajuda
    "app" -> R.drawable.ic_microfone
    else -> R.drawable.ic_novidade
}

/** A permissão "Sobrepor a outros apps" foi dada (confere de novo quando a pessoa volta das configurações do Android). */
@Composable
fun lembrarPermissaoJanela(): Boolean {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(JanelaControles.permitido(ctx)) }
    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ok = JanelaControles.permitido(ctx) }
        dono.lifecycle.addObserver(obs)
        onDispose { dono.lifecycle.removeObserver(obs) }
    }
    return ok
}

/** Botão "Permitir" (ou "Permissão concedida") da janela com botões sempre à vista. */
@Composable
fun PermissaoJanela(vm: AppVM, centralizado: Boolean = true) {
    val c = LocalCores.current
    val s = vm.tx
    val ctx = LocalContext.current
    val ok = lembrarPermissaoJanela()
    Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = if (centralizado) Alignment.Center else Alignment.CenterStart) {
        if (ok) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = c.ok, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(s["jc_ok"], color = c.ok, fontWeight = FontWeight.SemiBold)
        } else Button(onClick = {
            if (!vm.cfg.janelaControles) vm.mudarCfg { it.copy(janelaControles = true) }
            JanelaControles.pedirPermissao(ctx)
        }) { Text(s["jc_permitir"]) }
    }
}

/**
 * Telas deslizáveis: a apresentação (tipo "apresentacao") ou as novidades da versão ("novidades").
 * "Pular" sempre à vista, uma ideia por tela, caixa "Não mostrar mais ao abrir" no rodapé.
 */
@Composable
fun TelaGuia(vm: AppVM, tipo: String) {
    val c = LocalCores.current
    val s = vm.tx
    val compacto = LocalConfiguration.current.screenWidthDp < 600
    val lang = vm.cfg.lang
    val paginas: List<Pair<String?, Dicas.Dica>> = remember(tipo, lang) {
        val novas = if (tipo == "novidades") vm.novidadesPendentes() else emptyList()
        if (novas.isNotEmpty()) novas.flatMap { v -> v.itens.map { Textos.formatar(s["guia_novidades"], "v" to vm.nomeVersao(v)) to it } }
        else Dicas.apresentacao(lang, Produto.id, vm.nomeApp).map { null to it }
    }
    var naoMostrar by remember { mutableStateOf(vm.cfg.introOcultar) }
    val pager = rememberPagerState { paginas.size }
    val escopo = rememberCoroutineScope()
    val fechar = { vm.fecharGuia(naoMostrar) }
    val ultima = pager.currentPage == paginas.size - 1

    Dialog(onDismissRequest = fechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp), color = c.cartao,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(if (compacto) 0.94f else 0.9f)
                .heightIn(max = if (compacto) 760.dp else 640.dp).fillMaxHeight(if (compacto) 0.9f else 0.9f),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 14.dp)) {
                // topo: título das novidades (ou "Tela 1 de 5") e "Pular"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                    Text(
                        paginas[pager.currentPage].first ?: Textos.formatar(s["guia_pagina"], "n" to pager.currentPage + 1, "t" to paginas.size),
                        color = if (paginas[pager.currentPage].first != null) c.falaBorda else c.suave,
                        fontWeight = if (paginas[pager.currentPage].first != null) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    if (!ultima) TextButton(onClick = fechar) { Text(s["guia_pular"]) }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                    PaginaGuia(vm, paginas[i].second, compacto)
                }
                // bolinhas
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(paginas.size) { k ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (k == pager.currentPage) 10.dp else 8.dp)
                                .background(if (k == pager.currentPage) c.falaBorda else c.linha, CircleShape),
                        )
                    }
                }
                HorizontalDivider(color = c.linha, modifier = Modifier.padding(end = 8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clickable(role = Role.Checkbox) { naoMostrar = !naoMostrar },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(naoMostrar, { naoMostrar = it })
                    Text(s["guia_naoMostrar"], color = c.tinta, fontSize = 15.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pager.currentPage > 0) OutlinedButton(onClick = { escopo.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                        Text(s["guia_voltar"])
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (ultima) fechar() else escopo.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }) {
                        Text(when { !ultima -> s["guia_proximo"]; paginas[0].first != null -> s["guia_ok"]; else -> s["guia_comecar"] })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaginaGuia(vm: AppVM, d: Dicas.Dica, compacto: Boolean) {
    val c = LocalCores.current
    val s = vm.tx
    Box(Modifier.fillMaxSize().padding(end = 8.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // no celular, ícone e títulos menores para caber tudo sem rolar
            Box(Modifier.size(if (compacto) 60.dp else 84.dp).background(c.falaFundo, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(iconeDica(d.icone)), null, tint = c.falaBorda, modifier = Modifier.size(if (compacto) 30.dp else 42.dp))
            }
            Spacer(Modifier.height(if (compacto) 12.dp else 18.dp))
            Text(d.titulo, fontSize = if (compacto) 20.sp else 22.sp, fontWeight = FontWeight.Bold, color = c.tinta,
                textAlign = TextAlign.Center, lineHeight = if (compacto) 24.sp else 27.sp)
            Spacer(Modifier.height(if (compacto) 8.dp else 10.dp))
            Text(d.texto, fontSize = if (compacto) 15.sp else 16.sp, color = c.suave, textAlign = TextAlign.Center,
                lineHeight = if (compacto) 21.sp else 23.sp)
            when (d.acao) {
                "idioma" -> {
                    Spacer(Modifier.height(if (compacto) 12.dp else 18.dp))
                    Text(s["guia_idioma"], fontSize = 14.sp, color = c.suave)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.border(1.dp, c.linha, RoundedCornerShape(10.dp))) {
                        listOf("pt" to "Português", "es" to "Español").forEach { (v, rot) ->
                            val on = v == vm.cfg.lang
                            Text(rot, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else c.tinta,
                                modifier = Modifier.background(if (on) c.falaBorda else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.RadioButton) { vm.mudarIdioma(v) }
                                    .semantics { contentDescription = rot }
                                    .padding(horizontal = 18.dp, vertical = 12.dp))
                        }
                    }
                }
                "permissao" -> PermissaoJanela(vm)
            }
        }
    }
}

/** Configurações → Ajuda e dicas: rever a apresentação, ligar/desligar ao abrir, dicas e novidades. */
@Composable
fun PainelAjuda(vm: AppVM, aoApresentacao: () -> Unit, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    Painel(s["ajuda"], aoFechar) {
        Button(onClick = aoApresentacao, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
            Icon(painterResource(R.drawable.ic_play), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s["ajuda_ver"])
        }
        Linha(s["ajuda_mostrar"]) { Switch(!vm.cfg.introOcultar, { on -> vm.mudarCfg { it.copy(introOcultar = !on) } }) }

        Secao(s["ajuda_dicas"])
        val dicas = Dicas.apresentacao(vm.cfg.lang, Produto.id, vm.nomeApp).drop(1) + Dicas.ajuda(vm.cfg.lang, Produto.id)
        dicas.forEach { d ->
            ItemDica(d.icone, d.titulo, d.texto)
            if (d.acao == "permissao") Box(Modifier.padding(start = 52.dp, bottom = 6.dp)) { PermissaoJanela(vm, centralizado = false) }
            HorizontalDivider(color = c.linha)
        }

        Secao(s["ajuda_novidades"])
        Dicas.novidades(vm.cfg.lang, Produto.id).filter { it.codigo <= BuildConfig.VERSION_CODE }.forEach { v ->
            Text(Textos.formatar(s["ajuda_versao"], "v" to vm.nomeVersao(v)), fontWeight = FontWeight.Bold, color = c.tinta,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            v.itens.forEach { d ->
                Text("• ${d.titulo}", fontWeight = FontWeight.SemiBold, color = c.tinta, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Text(d.texto, color = c.suave, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text("${vm.nomeApp} · ${Textos.formatar(s["ajuda_versao"], "v" to BuildConfig.VERSION_NAME)}", color = c.suave, fontSize = 13.sp,
            modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun ItemDica(icone: String, titulo: String, texto: String) {
    val c = LocalCores.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).background(c.falaFundo, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(iconeDica(icone)), null, tint = c.falaBorda, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.SemiBold, color = c.tinta)
            Text(texto, color = c.suave, fontSize = 14.5.sp, lineHeight = 20.sp)
        }
    }
}
