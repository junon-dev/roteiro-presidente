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
    val compacto = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    androidx.compose.runtime.CompositionLocalProvider(LocalCompacto provides compacto) { Tela(vm) }
}

@Composable
private fun Tela(vm: AppVM) {
    val c = LocalCores.current
    val compacto = LocalCompacto.current
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
        while (presidindo && vm.relogioAutomatico) {
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
    // Rola até a parte só quando ela muda pelos botões da barra (o ▶ é tocado na própria parte, que já está na tela)
    var rolar by remember { mutableStateOf(true) }
    LaunchedEffect(passoIdx, presidindo) {
        if (presidindo && passo != null && rolar) indiceBloco[passo.bloco]?.let { lista.animateScrollToItem(it) }
        rolar = false
    }

    Scaffold(
        containerColor = c.fundo,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            BarraTopo(vm, aoPresidir = {
                if (vm.presidindo) { vm.pararPresidir(); painel = "relatorio" } else { rolar = true; vm.iniciarPresidir(passos.size) }
            }, aoPainel = { painel = it })
        },
        bottomBar = { if (presidindo && passo != null) BarraPresidir(vm, passos, passoIdx) { i -> rolar = true; vm.irPasso(i, passos.size) } },
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
        val palco = presidindo && vm.palco
        val ed = Edicao(est, vm::valor, vm::marcar, palco)
        val dens = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * if (palco) 1.15f else 1f)
        ) {
        val feitos = if (presidindo) passos.take(passoIdx).map { it.bloco }.toSet() - setOfNotNull(passo?.bloco) else emptySet()
        LazyColumn(
            state = lista,
            contentPadding = PaddingValues(start = if (compacto) 8.dp else 14.dp, end = if (compacto) 8.dp else 14.dp,
                top = pad.calculateTopPadding() + 10.dp, bottom = pad.calculateBottomPadding() + 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().imePadding(),
        ) {
            item(key = "cab") {
                Box(Modifier.widthIn(max = LARGURA_MAX.dp).fillMaxWidth()) {
                    Cabecalho(vm, est.sc, est.salas, palco, aoImprimir = {
                        Impressao.imprimir(ctx, s["app"] + " — " + sem.periodo,
                            Impressao.html(sem, est, rot, if (est.sc) s["sc"] else s["normal"]))
                    }, aoRelatorio = { painel = "relatorio" })
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
                                aoIniciar = if (presidindo) { cons ->
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
    }

    when (painel) {
        "licoes" -> PainelLicoes(vm) { painel = null }
        "config" -> PainelConfig(vm, aoCanticos = { painel = "canticos" }) { painel = null }
        "canticos" -> PainelCanticos(vm) { painel = "config" }
        "relatorio" -> sem?.let { PainelRelatorio(vm, it) { painel = null } }
    }
    vm.resultado?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.resultado = null },
            title = { Text(s["resultadoImp"]) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.resultado = null }) { Text("OK") } },
        )
    }
    // "Lendo arquivo…": janela própria, para aparecer por cima do painel de Configurações
    vm.ocupado?.let { msg ->
        androidx.compose.ui.window.Dialog(onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
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
private fun SeletorSemana(vm: AppVM, modifier: Modifier = Modifier) {
    val s = vm.tx
    val sem = vm.semanaAtual()
    var menu by remember { mutableStateOf(false) }
    Row(modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.moverSemana(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], tint = Color.White) }
        Box(Modifier.weight(1f, fill = false)) {
            Row(Modifier.clickable { menu = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sem?.let { "${it.periodo} · ${it.leitura}" } ?: s["nenhumaSemana"], fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).widthIn(max = 320.dp))
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
}

@Composable
private fun SeletorIdioma(vm: AppVM, compacto: Boolean) {
    val c = LocalCores.current
    Row(Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))) {
        for (l in listOf("pt", "es")) {
            val on = vm.cfg.lang == l
            Text(l.uppercase(), fontWeight = FontWeight.Bold, color = if (on) c.barra else Color.White, fontSize = if (compacto) 13.sp else 14.sp,
                modifier = Modifier.background(if (on) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { vm.mudarIdioma(l) }.padding(horizontal = if (compacto) 10.dp else 14.dp, vertical = if (compacto) 8.dp else 11.dp))
        }
    }
}

@Composable
private fun BarraTopo(vm: AppVM, aoPresidir: () -> Unit, aoPainel: (String) -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val compacto = LocalCompacto.current
    Surface(color = c.barra, contentColor = Color.White) {
        if (compacto) {
            // Celular: título e ícones numa linha, semana na linha de baixo
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s["app"], fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    SeletorIdioma(vm, true)
                    if (vm.presidindo) IconeTopo(s["palco"], ativo = vm.palco, onClick = { vm.palco = !vm.palco }) {
                        Icon(painterResource(R.drawable.ic_palco), null, Modifier.size(22.dp))
                    }
                    IconeTopo(if (vm.presidindo) s["sair"] else s["presidir"], ativo = vm.presidindo, onClick = aoPresidir) {
                        if (vm.presidindo) Icon(painterResource(R.drawable.ic_parar), null, Modifier.size(22.dp))
                        else Icon(Icons.Filled.PlayArrow, null, Modifier.size(24.dp))
                    }
                    IconeTopo(s["licoes"], onClick = { aoPainel("licoes") }) { Icon(painterResource(R.drawable.ic_livro), null, Modifier.size(22.dp)) }
                    IconeTopo(s["config"], onClick = { aoPainel("config") }) { Icon(Icons.Filled.Settings, null, Modifier.size(22.dp)) }
                }
                SeletorSemana(vm, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp))
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) {
                    Text(s["app"], fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text(s["appsub"], fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                }
                SeletorSemana(vm, Modifier.align(Alignment.CenterVertically))
                Box(Modifier.align(Alignment.CenterVertically)) { SeletorIdioma(vm, false) }
                BotaoTopo(if (vm.presidindo) s["sair"] else s["presidir"], ativo = vm.presidindo, onClick = aoPresidir) {
                    if (vm.presidindo) Icon(painterResource(R.drawable.ic_parar), null, Modifier.size(20.dp))
                    else Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                }
                if (vm.presidindo) BotaoTopo(s["palco"], ativo = vm.palco, onClick = { vm.palco = !vm.palco }) {
                    Icon(painterResource(R.drawable.ic_palco), null, Modifier.size(20.dp))
                }
                BotaoTopo(s["licoes"], onClick = { aoPainel("licoes") }) { Icon(painterResource(R.drawable.ic_livro), null, Modifier.size(20.dp)) }
                BotaoTopo(s["config"], onClick = { aoPainel("config") }) { Icon(Icons.Filled.Settings, null, Modifier.size(20.dp)) }
            }
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

/** Botão só com ícone (barra do celular). */
@Composable
private fun IconeTopo(descricao: String, ativo: Boolean = false, onClick: () -> Unit, icone: @Composable () -> Unit) {
    val c = LocalCores.current
    Box(
        Modifier.padding(start = 4.dp).size(42.dp)
            .background(if (ativo) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClickLabel = descricao, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides (if (ativo) c.barra else Color.White)) { icone() }
    }
}

@Composable
private fun Cabecalho(vm: AppVM, sc: Boolean, salas: Boolean, palco: Boolean, aoImprimir: () -> Unit, aoRelatorio: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val sem = vm.semanaAtual() ?: return
    val ctx = LocalContext.current
    val compacto = LocalCompacto.current
    var lembretes by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c.cartao, RoundedCornerShape(14.dp))
            .padding(horizontal = if (compacto) 12.dp else 16.dp, vertical = if (compacto) 10.dp else 14.dp)
    ) {
        Text("${s["meeting"]} · ${sem.leitura} · ${sem.periodo}".uppercase(), fontSize = 13.sp, color = c.suave,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Text(if (sc) s["sc"] else s["normal"], fontSize = if (compacto) 20.sp else 24.sp, fontWeight = FontWeight.Bold, color = c.tinta,
            modifier = Modifier.padding(top = 2.dp, bottom = if (palco) 0.dp else 10.dp))
        if (palco) return@Column   // no modo palco, só o título
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
            if (vm.relatorio(sem.id) != null) AssistChip(onClick = aoRelatorio, label = { Text(s["relatorio"]) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_relogio), null, Modifier.size(18.dp)) })
        }
        Text((if (lembretes) "▾ " else "▸ ") + s["lembretes_t"], fontWeight = FontWeight.SemiBold, color = c.tinta,
            modifier = Modifier.clickable { lembretes = !lembretes }.padding(top = 10.dp, bottom = 4.dp))
        if (lembretes) s.lista("lembretes").forEach {
            Text("•  $it", color = c.suave, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun BarraPresidir(vm: AppVM, passos: List<app.roteiro.presidente.nucleo.Passo>, idx: Int, aoMudarPasso: (Int) -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val compacto = LocalCompacto.current
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
    // Relógio da reunião: corre sempre, desde o início do modo Presidir
    val reuniao = vm.decorridoReuniao().coerceAtLeast(0.0)
    val rs = reuniao.toLong()
    val txtReuniao = "${rs / 3600}:" + ((rs / 60) % 60).toString().padStart(2, '0') + ":" + (rs % 60).toString().padStart(2, '0')
    val faltam = Math.round(105 - reuniao / 60).toInt()
    val txtFaltam = if (faltam >= 0) Textos.formatar(s["faltam"], "n" to faltam) else "+${-faltam} min"
    // adiantado/atrasado: compara o tempo real da reunião com o previsto para este ponto do roteiro
    val previstoMin = (p.ini - Roteiro.minDe(vm.cfg.inicio)) + minOf(dec, total) / 60.0
    val dif = Math.round(reuniao / 60 - previstoMin).toInt()
    val status = when {
        Math.abs(dif) < 1 -> s["noHorario"]
        dif > 0 -> "${s["atrasado"]} $dif min"
        else -> "${s["adiantado"]} ${-dif} min"
    }
    val corStatus = when { Math.abs(dif) < 1 -> Color.White; dif > 0 -> Color(0xFFFFB4A8); else -> Color(0xFF8FE3B0) }
    val linhaStatus = "${s["previsto"]} ${Roteiro.hm(p.ini)} · $status" + if (pausado) " · ${s["pausado"]}" else ""

    @Composable
    fun Botoes(tam: Int) {
        BotaoBarra(tamanho = tam, onClick = { aoMudarPasso(idx - 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], Modifier.size(30.dp)) }
        Spacer(Modifier.width(8.dp))
        BotaoBarra(tamanho = tam, onClick = { vm.pausar() }) {
            if (pausado) Icon(Icons.Filled.PlayArrow, s["continuar"], Modifier.size(30.dp))
            else Icon(painterResource(R.drawable.ic_pausa), s["pausar"], Modifier.size(30.dp))
        }
        Spacer(Modifier.width(8.dp))
        BotaoBarra(tamanho = tam, destaque = true, onClick = {
            if (idx < passos.size - 1) aoMudarPasso(idx + 1) else vm.avisar(s["fim"])
        }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s["proxPasso"], Modifier.size(32.dp), tint = c.barra) }
    }

    Surface(color = c.barra, contentColor = Color.White, shadowElevation = 8.dp) {
        if (compacto) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(p.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(linhaStatus, fontSize = 13.sp, color = corStatus, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(txtRel, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = if (pausado) 0.6f else 1f),
                        modifier = Modifier.background(corRel, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(txtReuniao, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(txtFaltam, fontSize = 12.sp, color = if (faltam < 0) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.8f))
                    }
                    Botoes(48)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s["parte"].uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(txtRel, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = if (pausado) 0.6f else 1f),
                        modifier = Modifier.background(corRel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(s["reuniao"].uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(txtReuniao, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(txtFaltam, fontSize = 13.sp, color = if (faltam < 0) Color(0xFFFFB4A8) else Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(linhaStatus, fontSize = 14.sp, color = corStatus)
                }
                Botoes(58)
            }
        }
    }
}

@Composable
private fun BotaoBarra(tamanho: Int = 58, destaque: Boolean = false, onClick: () -> Unit, conteudo: @Composable () -> Unit) {
    Box(
        Modifier.size(width = if (destaque) (tamanho * 1.28f).dp else tamanho.dp, height = tamanho.dp)
            .background(if (destaque) Color.White else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}

