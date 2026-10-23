@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.roteiro.presidente.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roteiro.presidente.AppVM
import app.roteiro.presidente.R
import app.roteiro.presidente.dados.Impressao
import app.roteiro.presidente.nucleo.ItemRoteiro
import app.roteiro.presidente.nucleo.Roteiro
import app.roteiro.presidente.nucleo.Textos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private const val LARGURA_MAX = 940

@Composable
fun TelaPrincipal(vm: AppVM) {
    val c = LocalCores.current
    val s = vm.tx
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var painel by rememberSaveable { mutableStateOf<String?>(null) }   // "licoes", "config", "canticos"

    LaunchedEffect(Unit) { vm.mensagens.collect { snack.showSnackbar(it) } }

    val sem = vm.semanaAtual()
    val est = sem?.let { vm.estado(it.id) }
    val rot = sem?.let { vm.roteiro(it) }
    val presidindo = vm.presidindo && rot != null
    val passos = rot?.passos.orEmpty()
    val pres = vm.presidir
    val passoIdx = if (presidindo) (pres?.passo ?: 0).coerceIn(0, (passos.size - 1).coerceAtLeast(0)) else -1
    val passo = passos.getOrNull(passoIdx)

    // Tela ligada durante a reunião
    val view = LocalView.current
    val manterLigada = presidindo && vm.cfg.tela
    DisposableEffect(manterLigada) {
        view.keepScreenOn = manterLigada
        onDispose { view.keepScreenOn = false }
    }
    // Relógio
    LaunchedEffect(presidindo, passoIdx) {
        while (presidindo) {
            vm.agora = System.currentTimeMillis()
            passo?.let { vm.verificarFimDoTempo(it.dur) }
            delay(250)
        }
    }
    BackHandler(enabled = presidindo && painel == null) { vm.avisar(s["usarSair"]) }

    val lista = rememberLazyListState()
    // índice na lista de cada bloco (o item 0 é o cabeçalho)
    val indiceBloco: Map<String, Int> = remember(rot) {
        val m = HashMap<String, Int>()
        rot?.itens?.forEachIndexed { i, it -> if (it is ItemRoteiro.BlocoR) m[it.b.id] = i + 1 }
        m
    }
    LaunchedEffect(passoIdx, presidindo) {
        if (presidindo && passo != null) indiceBloco[passo.bloco]?.let { lista.animateScrollToItem(it) }
    }

    Scaffold(
        containerColor = c.fundo,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            BarraTopo(vm, aoPresidir = {
                if (vm.presidindo) vm.pararPresidir() else vm.iniciarPresidir(passos.size)
            }, aoPainel = { painel = it })
        },
        bottomBar = { if (presidindo && passo != null) BarraPresidir(vm, passos, passoIdx) },
    ) { pad ->
        if (sem == null || rot == null || est == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Textos.formatar(s["vazio"], "i" to Textos.NOME_IDIOMA[vm.cfg.lang]), color = c.suave, fontSize = 18.sp,
                        modifier = Modifier.padding(24.dp))
                    TextButton(onClick = { painel = "config" }) { Text(s["config"]) }
                }
            }
            return@Scaffold
        }
        val ed = Edicao(est, vm::valor, vm::marcar)
        val feitos = if (presidindo) passos.take(passoIdx).map { it.bloco }.toSet() - setOfNotNull(passo?.bloco) else emptySet()
        LazyColumn(
            state = lista,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 10.dp, bottom = pad.calculateBottomPadding() + 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            item(key = "cab") {
                Box(Modifier.widthIn(max = LARGURA_MAX.dp).fillMaxWidth()) {
                    Cabecalho(vm, est.sc, est.salas, aoImprimir = {
                        Impressao.imprimir(ctx, s["app"] + " — " + sem.periodo,
                            Impressao.html(sem, est, rot, if (est.sc) s["sc"] else s["normal"]))
                    })
                }
            }
            itemsIndexed(rot.itens, key = { i, it -> if (it is ItemRoteiro.BlocoR) it.b.id else "i$i" }) { _, item ->
                Box(Modifier.widthIn(max = LARGURA_MAX.dp).fillMaxWidth()) {
                    when (item) {
                        is ItemRoteiro.Secao -> TituloSecao(item.sec, item.titulo)
                        is ItemRoteiro.Dica -> Dica(item.texto, item.sc)
                        is ItemRoteiro.BlocoR -> {
                            val b = item.b
                            val ehAtual = passo?.bloco == b.id
                            CartaoBloco(
                                b, ed, sem.idioma,
                                atual = ehAtual && passo?.cons == false,
                                consAtual = ehAtual && passo?.cons == true,
                                feito = b.id in feitos,
                                aoTocar = if (presidindo) { cons ->
                                    val alvo = passos.indexOfFirst { it.bloco == b.id && it.cons == cons }
                                        .takeIf { it >= 0 } ?: passos.indexOfFirst { it.bloco == b.id }
                                    if (alvo >= 0 && alvo != passoIdx) vm.irPasso(alvo, passos.size)
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }

    when (painel) {
        "licoes" -> PainelLicoes(vm) { painel = null }
        "config" -> PainelConfig(vm, aoCanticos = { painel = "canticos" }) { painel = null }
        "canticos" -> PainelCanticos(vm) { painel = "config" }
    }
    vm.ocupado?.let { msg ->
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(msg)
                }
            }
        }
    }
}

@Composable
private fun BarraTopo(vm: AppVM, aoPresidir: () -> Unit, aoPainel: (String) -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val sem = vm.semanaAtual()
    var menu by remember { mutableStateOf(false) }
    Surface(color = c.barra, contentColor = Color.White) {
        FlowRow(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) {
                Text(s["app"], fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(s["appsub"], fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            Row(
                Modifier.align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.moverSemana(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], tint = Color.White) }
                Box {
                    Row(Modifier.clickable { menu = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sem?.let { "${it.periodo} · ${it.leitura}" } ?: s["nenhumaSemana"], fontWeight = FontWeight.SemiBold,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp))
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        vm.semanasIdioma().forEach { w ->
                            DropdownMenuItem(text = {
                                Text("${w.periodo} · ${w.leitura}", fontWeight = if (w.id == sem?.id) FontWeight.Bold else FontWeight.Normal)
                            }, onClick = { menu = false; vm.irParaSemana(w.id) })
                        }
                    }
                }
                IconButton(onClick = { vm.moverSemana(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s["proxPasso"], tint = Color.White) }
            }
            Row(Modifier.align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))) {
                for (l in listOf("pt", "es")) {
                    val on = vm.cfg.lang == l
                    Text(l.uppercase(), fontWeight = FontWeight.Bold, color = if (on) c.barra else Color.White,
                        modifier = Modifier.background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { vm.mudarIdioma(l) }.padding(horizontal = 14.dp, vertical = 11.dp))
                }
            }
            BotaoTopo(if (vm.presidindo) s["sair"] else s["presidir"], ativo = vm.presidindo, onClick = aoPresidir) {
                if (vm.presidindo) Icon(painterResource(R.drawable.ic_parar), null, Modifier.size(20.dp))
                else Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
            }
            BotaoTopo(s["licoes"], onClick = { aoPainel("licoes") }) { Icon(painterResource(R.drawable.ic_livro), null, Modifier.size(20.dp)) }
            BotaoTopo(s["config"], onClick = { aoPainel("config") }) { Icon(Icons.Filled.Settings, null, Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun BotaoTopo(texto: String, ativo: Boolean = false, onClick: () -> Unit, icone: @Composable () -> Unit) {
    val c = LocalCores.current
    Row(
        Modifier.background(if (ativo) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides (if (ativo) c.barra else Color.White)) { icone() } }
        Spacer(Modifier.width(6.dp))
        Text(texto, fontWeight = FontWeight.SemiBold, color = if (ativo) c.barra else Color.White)
    }
}

@Composable
private fun Cabecalho(vm: AppVM, sc: Boolean, salas: Boolean, aoImprimir: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val sem = vm.semanaAtual() ?: return
    val ctx = LocalContext.current
    var lembretes by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c.cartao, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("${s["meeting"]} · ${sem.leitura} · ${sem.periodo}".uppercase(), fontSize = 13.sp, color = c.suave,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Text(if (sc) s["sc"] else s["normal"], fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.tinta,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sc, onClick = { vm.setSC(!sc) }, label = { Text(s["visitaSC"]) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.scFundo, selectedLabelColor = c.sc))
            FilterChip(selected = salas && !sc, enabled = !sc, onClick = { vm.setSalas(!salas) }, label = { Text(s["salas"]) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.salaFundo, selectedLabelColor = c.sala))
            AssistChip(onClick = {
                val m = Roteiro.minDe(vm.cfg.inicio)
                TimePickerDialog(ctx, { _, h, mi -> vm.mudarCfg { it.copy(inicio = "%02d:%02d".format(h, mi)) } }, m / 60, m % 60, true).show()
            }, label = { Text("${s["inicio"]}  ${vm.cfg.inicio}") })
            AssistChip(onClick = aoImprimir, label = { Text(s["imprimir"]) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_imprimir), null, Modifier.size(18.dp)) })
        }
        Text((if (lembretes) "▾ " else "▸ ") + s["lembretes_t"], fontWeight = FontWeight.SemiBold, color = c.tinta,
            modifier = Modifier.clickable { lembretes = !lembretes }.padding(top = 10.dp, bottom = 4.dp))
        if (lembretes) s.lista("lembretes").forEach {
            Text("•  $it", color = c.suave, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun BarraPresidir(vm: AppVM, passos: List<app.roteiro.presidente.nucleo.Passo>, idx: Int) {
    val c = LocalCores.current
    val s = vm.tx
    val p = passos[idx]
    val pres = vm.presidir
    val dec = vm.decorrido()
    val total = p.dur * 60.0
    val resta = total - dec
    val pausado = pres?.pausadoEm != null
    val corRel = when {
        p.dur == 0 -> Color.White.copy(alpha = 0.08f)
        resta < 0 -> c.erro
        resta <= maxOf(15.0, total * 0.2) -> c.alerta
        else -> Color.White.copy(alpha = 0.08f)
    }
    val txtRel = if (p.dur == 0) "—" else {
        val v = Math.abs(resta).toLong()
        (if (resta < 0) "+" else "") + "${v / 60}:" + (v % 60).toString().padStart(2, '0')
    }
    val agora = LocalTime.now()
    val minAgora = agora.hour * 60 + agora.minute + agora.second / 60.0
    val dif = Math.round(minAgora - (p.ini + dec / 60.0)).toInt()
    val status = when {
        Math.abs(dif) > 60 -> "${s["agora"]} ${Roteiro.hm(minAgora.toInt())}"
        Math.abs(dif) < 1 -> s["noHorario"]
        dif > 0 -> "${s["atrasado"]} $dif min"
        else -> "${s["adiantado"]} ${-dif} min"
    }
    val corStatus = when { Math.abs(dif) > 60 || Math.abs(dif) < 1 -> Color.White; dif > 0 -> Color(0xFFFFB4A8); else -> Color(0xFF8FE3B0) }
    Surface(color = c.barra, contentColor = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(txtRel, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = if (pausado) 0.6f else 1f),
                modifier = Modifier.background(corRel, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 4.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${idx + 1}/${passos.size} · ${p.titulo}", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s["previsto"]} ${Roteiro.hm(p.ini)} · $status" + if (pausado) " · ${s["pausado"]}" else "",
                    fontSize = 14.sp, color = corStatus)
            }
            BotaoBarra(onClick = { vm.irPasso(idx - 1, passos.size) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], Modifier.size(30.dp)) }
            Spacer(Modifier.width(8.dp))
            BotaoBarra(onClick = { vm.pausar() }) {
                if (pausado) Icon(Icons.Filled.PlayArrow, s["continuar"], Modifier.size(30.dp))
                else Icon(painterResource(R.drawable.ic_pausa), s["pausar"], Modifier.size(30.dp))
            }
            Spacer(Modifier.width(8.dp))
            BotaoBarra(destaque = true, onClick = {
                if (idx < passos.size - 1) vm.irPasso(idx + 1, passos.size) else vm.avisar(s["fim"])
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s["proxPasso"], Modifier.size(32.dp), tint = c.barra) }
        }
    }
}

@Composable
private fun BotaoBarra(destaque: Boolean = false, onClick: () -> Unit, conteudo: @Composable () -> Unit) {
    Box(
        Modifier.size(width = if (destaque) 74.dp else 58.dp, height = 58.dp)
            .background(if (destaque) Color.White else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}

