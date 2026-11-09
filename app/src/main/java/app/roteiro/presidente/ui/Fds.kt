@file:OptIn(ExperimentalLayoutApi::class)

package app.roteiro.presidente.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roteiro.presidente.AppVM
import app.roteiro.presidente.dados.Impressao
import app.roteiro.presidente.nucleo.CamposFds
import app.roteiro.presidente.nucleo.EventoEspecial
import app.roteiro.presidente.nucleo.Textos
import java.time.LocalDate

/* ---------- abas: meio de semana / fim de semana / Celebração ---------- */
@Composable
fun AbasModo(vm: AppVM, modifier: Modifier = Modifier) {
    if (app.roteiro.presidente.Produto.modos.size < 2) return
    val c = LocalCores.current
    val s = vm.tx
    Row(modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).padding(3.dp)) {
        for ((m, rot) in listOf("meio" to s["aba_meio"], "fds" to s["aba_fds"], "cel" to s["aba_cel"]).filter { it.first in app.roteiro.presidente.Produto.modos }) {
            val on = vm.modo == m
            Box(
                Modifier.weight(1f).background(if (on) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { vm.mudarModo(m) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(rot, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (on) c.barra else Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ---------- seletores de fim de semana e de ano da Celebração ---------- */
@Composable
fun SeletorFds(vm: AppVM, modifier: Modifier = Modifier) {
    val s = vm.tx
    var menu by remember { mutableStateOf(false) }
    Row(modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.moverFds(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], tint = Color.White) }
        Box(Modifier.weight(1f, fill = false)) {
            Row(Modifier.clickable { menu = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Textos.formatar(s["fds_sel"], "d" to vm.dataFdsTexto(vm.fdsSemana)), fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).widthIn(max = 340.dp))
                Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                vm.semanasAgenda(false).take(16).forEach { seg ->
                    val r = vm.resumoFds(vm.cfg.lang, seg)
                    val det = when { r.sc -> s["visitaSC"]; r.especial -> s["fds_especial"]; r.tema.isNotBlank() -> r.tema; else -> "" }
                    DropdownMenuItem(text = {
                        Text(vm.dataFdsTexto(seg) + if (det.isNotBlank()) " · $det" else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (seg == vm.fdsSemana) FontWeight.Bold else FontWeight.Normal)
                    }, onClick = { menu = false; vm.irParaFds(seg) })
                }
            }
        }
        IconButton(onClick = { vm.moverFds(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s["proxPasso"], tint = Color.White) }
    }
}

@Composable
fun SeletorCel(vm: AppVM, modifier: Modifier = Modifier) {
    val s = vm.tx
    Row(modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.moverCel(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s["passoAnt"], tint = Color.White) }
        Text(Textos.formatar(s["cel_sel"], "a" to vm.celAno), fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp))
        IconButton(onClick = { vm.moverCel(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s["proxPasso"], tint = Color.White) }
    }
}

/* ---------- campos do formulário ---------- */
@Composable
private fun CampoF(k: String, rotulo: String, ed: Edicao, largura: Dp = 230.dp, numero: Boolean = false, ajuda: String? = null) {
    val c = LocalCores.current
    val compacto = LocalCompacto.current
    OutlinedTextField(
        value = ed.estado.valor(k),
        onValueChange = { v -> ed.valor(k, if (numero) v.filter { it.isDigit() }.take(3) else v) },
        label = { Text(rotulo) },
        singleLine = true,
        supportingText = ajuda?.let { { Text(it, color = c.suave, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        keyboardOptions = if (numero) KeyboardOptions(keyboardType = KeyboardType.Number)
                          else KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = if (compacto && !numero) Modifier.fillMaxWidth() else Modifier.width(largura),
    )
}

/** Título e texto base do cântico, para mostrar embaixo do número. */
private fun ajudaCantico(vm: AppVM, ed: Edicao, k: String): String? {
    val n = ed.estado.valor(k).trim().toIntOrNull() ?: return null
    return listOfNotNull(vm.tituloCantico(vm.cfg.lang, n), vm.textoCantico(vm.cfg.lang, n)).joinToString(" · ").ifBlank { null }
}

@Composable
private fun CartaoForm(titulo: String, conteudo: @Composable () -> Unit) {
    val c = LocalCores.current
    val compacto = LocalCompacto.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c.cartao, RoundedCornerShape(14.dp))
            .border(1.dp, c.linha, RoundedCornerShape(14.dp))
            .padding(horizontal = if (compacto) 12.dp else 16.dp, vertical = 12.dp)
    ) {
        Text(titulo.uppercase(), fontSize = 13.sp, color = c.suave, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 6.dp))
        conteudo()
    }
}

@Composable
private fun Grade(conteudo: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { conteudo() }
}

/* ---------- formulário do fim de semana ---------- */
@Composable
fun FormFds(vm: AppVM, reu: AppVM.Reuniao, ed: Edicao, aoTemas: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val lang = vm.cfg.lang
    val e = reu.estado
    val compacto = LocalCompacto.current
    val esp = !e.sc && e.marcado(CamposFds.ESPECIAL)
    CartaoForm(s["prog_t"]) {
        when {
            e.sc -> {
                Grade {
                    CampoF(CamposFds.SC, s["f_sc_nome"], ed, 260.dp)
                    CampoF(CamposFds.SC_TEMA, s["f_scTema"], ed, 420.dp)
                    CampoF(CamposFds.SC_TEMA_F, s["f_scTemaF"], ed, 420.dp)
                }
            }
            esp -> {
                val ev = vm.evento("especial", lang)
                Text(buildString {
                    append(s["fds_t_esp"]); ev?.let { append(": “${it.tema}”"); it.cantico?.let { n -> append(" · ${s["f_cantico_n"]} $n") } }
                }, fontWeight = FontWeight.SemiBold, color = c.tinta)
                Text(s["prog_esp_info"], color = c.suave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                Grade {
                    CampoF(CamposFds.ORADOR, s["f_orador_"], ed, 260.dp)
                    CampoF(CamposFds.CONG, s["f_cong"], ed, 240.dp)
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.Top) {
                    CampoF(CamposFds.DISC, s["f_ndisc"], ed, if (compacto) 132.dp else 140.dp, numero = true)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = e.valor(CamposFds.TEMA), onValueChange = { ed.valor(CamposFds.TEMA, it) },
                        label = { Text(s["f_tema"]) }, minLines = 1, maxLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                    if (app.roteiro.presidente.Produto.agenda)
                        IconButton(onClick = aoTemas, modifier = Modifier.padding(top = 6.dp)) { Icon(Icons.AutoMirrored.Filled.List, s["temas"]) }
                }
                val n = e.valor(CamposFds.DISC).trim().toIntOrNull()
                val aviso = if (app.roteiro.presidente.Produto.agenda) vm.avisoDiscurso(lang, vm.fdsSemana, n)
                            else n?.let { vm.tituloDiscurso(lang, it) }?.takeIf { vm.naoUse(it) }?.let { s["nao_use"] }
                if (aviso != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = c.alerta, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(aviso, color = c.alerta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                } else if (vm.discursos(lang).isEmpty()) Text(Textos.formatar(s["sem_lista"], "i" to Textos.NOME_IDIOMA[lang]),
                    color = c.suave, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                Grade {
                    CampoF(CamposFds.ORADOR, s["f_orador_"], ed, 260.dp)
                    CampoF(CamposFds.CONG, s["f_cong"], ed, 240.dp)
                }
            }
        }
        Grade {
            if (!esp) CampoF(CamposFds.CANT, s["f_cantico_n"], ed, 230.dp, numero = true, ajuda = ajudaCantico(vm, ed, CamposFds.CANT))
            CampoF(CamposFds.ORA_INI, s["f_oracaoIni"], ed, 240.dp)
        }
        Grade {
            CampoF(CamposFds.DIR, s["f_dir_s"], ed, 240.dp)
            CampoF(CamposFds.LEITOR, s["f_leitor_s"], ed, 220.dp)
            CampoF(CamposFds.ARTIGO, s["f_artigo"], ed, 320.dp)
        }
        Grade {
            CampoF(CamposFds.CANT_S, s["f_cantS"], ed, 230.dp, numero = true, ajuda = ajudaCantico(vm, ed, CamposFds.CANT_S))
            CampoF(CamposFds.CANT_F, s["f_cantF"], ed, 230.dp, numero = true, ajuda = ajudaCantico(vm, ed, CamposFds.CANT_F))
            CampoF(CamposFds.ORA_FIM, s["f_oracaoF"], ed, 240.dp)
        }
    }
}

/* ---------- formulário da Celebração ---------- */
@Composable
fun FormCel(vm: AppVM, reu: AppVM.Reuniao, ed: Edicao) {
    val c = LocalCores.current
    val s = vm.tx
    val ev = vm.evento("celebracao", vm.cfg.lang)
    CartaoForm(s["prog_cel"]) {
        if (ev != null) Text("“${ev.tema}” · ${s["f_cantico_n"]} ${ev.cantico ?: "?"} / ${ev.canticoFinal ?: "?"}",
            fontWeight = FontWeight.SemiBold, color = c.tinta)
        Text(s["prog_cel_info"], color = c.suave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        Grade {
            CampoF(CamposFds.DATA, s["f_data_cel"], ed, 300.dp)
            CampoF(CamposFds.ORADOR, s["f_orador_"], ed, 260.dp)
            CampoF(CamposFds.ORA_INI, s["f_oracaoIni"], ed, 240.dp)
        }
    }
}

/* ---------- agenda de oradores ---------- */
@Composable
fun PainelAgenda(vm: AppVM, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val ctx = LocalContext.current
    val lang = vm.cfg.lang
    var anteriores by rememberSaveable { mutableStateOf(false) }
    val semanas = vm.semanasAgenda(anteriores)
    val hoje = vm.segundaDe(LocalDate.now())

    fun linha(seg: String): List<String> {
        val r = vm.resumoFds(lang, seg)
        val d = vm.dataCurta(vm.dataReuniaoFds(seg))
        return when {
            r.sc -> listOf(d, "", s["fds_t_sc"], vm.estado(vm.idFds(lang, seg)).valor(CamposFds.SC), "")
            r.especial -> listOf(d, "", s["fds_t_esp"] + ": " + (vm.evento("especial", lang)?.tema ?: ""), r.orador, r.cong)
            else -> listOf(d, r.discurso, r.tema, r.orador, r.cong)
        }
    }
    val cab = listOf("Data", s["f_ndisc"], s["f_tema"], s["f_orador_"], s["f_cong"]).let { if (lang == "es") listOf("Fecha") + it.drop(1) else it }

    Painel(s["ag_t"], aoFechar, rolavel = false) {
        Text(s["ag_info"], color = c.suave, fontSize = 15.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(anteriores, { anteriores = it }); Spacer(Modifier.width(6.dp)); Text(s["ag_anteriores"], color = c.tinta)
            }
            OutlinedButton(onClick = {
                val linhas = semanas.filter { it >= hoje }.map { linha(it) }
                Impressao.imprimir(ctx, s["ag_t"], Impressao.htmlAgenda(s["ag_t"], cab, linhas))
            }) { Text(s["ag_imprimir"]) }
            OutlinedButton(onClick = {
                val txt = s["ag_t"] + "\n\n" + semanas.filter { it >= hoje }.map { linha(it) }.filter { it.drop(1).any { v -> v.isNotBlank() } }
                    .joinToString("\n") { l -> l[0] + " — " + listOf(l[1].ifBlank { null }?.let { "nº $it" }, l[2].ifBlank { null },
                        l[3].ifBlank { null }, l[4].ifBlank { null }).filterNotNull().joinToString(" · ") }
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, txt) }
                ctx.startActivity(android.content.Intent.createChooser(i, s["ag_compartilhar"]))
            }) { Text(s["ag_compartilhar"]) }
        }
        HorizontalDivider(color = c.linha)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(semanas, key = { it }) { seg ->
                val r = vm.resumoFds(lang, seg)
                val atual = seg == vm.fdsSemana
                Column(Modifier.fillMaxWidth().background(if (atual) c.falaFundo else Color.Transparent)
                    .clickable { vm.irParaFds(seg); aoFechar() }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.dataFdsTexto(seg), fontWeight = FontWeight.Bold, color = if (seg < hoje) c.suave else c.tinta, modifier = Modifier.weight(1f))
                        if (r.sc) Etiqueta(s["visitaSC"], c.sc)
                        if (r.especial && !r.sc) Etiqueta(s["fds_especial"], c.sala)
                    }
                    val l = linha(seg)
                    val tema = listOfNotNull(l[1].ifBlank { null }?.let { "nº $it" }, l[2].ifBlank { null }).joinToString(" · ")
                    val quem = listOf(l[3], l[4]).filter { it.isNotBlank() }.joinToString(" · ")
                    if (tema.isBlank() && quem.isBlank()) Text(s["ag_vazio"], color = c.suave, fontStyle = FontStyle.Italic)
                    else {
                        if (tema.isNotBlank()) Text(tema, color = c.tinta)
                        if (quem.isNotBlank()) Text(quem, color = c.suave, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(color = c.linha)
            }
        }
    }
}

@Composable
private fun Etiqueta(t: String, cor: Color) {
    Text(t.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor,
        modifier = Modifier.padding(start = 6.dp).border(1.5.dp, cor, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp))
}

/* ---------- temas (S-99) e histórico ---------- */
@Composable
fun PainelTemas(vm: AppVM, escolher: ((Int) -> Unit)?, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val ctx = LocalContext.current
    val lang = vm.cfg.lang
    var busca by rememberSaveable { mutableStateOf("") }
    var aberto by rememberSaveable { mutableStateOf<Int?>(null) }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importar(uris) }
    val lista = vm.discursos(lang).entries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.sortedBy { it.first }
    val hist = remember(vm.dados, lang) { vm.historico(lang) }
    val hoje = LocalDate.now()
    val q = busca.trim().lowercase()
    val filtrada = if (q.isEmpty()) lista else lista.filter { (n, t) -> n.toString() == q || t.lowercase().contains(q) }

    Painel(s["te_t"] + " (S-99)", aoFechar, rolavel = false) {
        Text(s["te_info"], color = c.suave, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(busca, { busca = it }, label = { Text(s["te_busca"]) }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { abrir.launch(arrayOf("application/pdf", "*/*")) }) { Text(s["te_importar"]) }
        }
        if (lista.isEmpty()) Text(Textos.formatar(s["te_vazio"], "i" to Textos.NOME_IDIOMA[lang]), color = c.suave, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(filtrada, key = { it.first }) { (n, t) ->
                val datas = hist[n].orEmpty()
                val passado = datas.filter { it <= hoje }.maxOrNull()
                val futuro = datas.filter { it > hoje }.minOrNull()
                val recente = passado != null && java.time.temporal.ChronoUnit.MONTHS.between(passado, hoje) < 12
                val naoUse = vm.naoUse(t)
                Column(Modifier.fillMaxWidth().clickable(enabled = !naoUse) { aberto = if (aberto == n) null else n }.padding(vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("$n", fontWeight = FontWeight.Bold, color = if (naoUse) c.suave else c.tinta, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t, color = if (naoUse) c.suave else c.tinta, fontStyle = if (naoUse) FontStyle.Italic else FontStyle.Normal)
                            if (!naoUse) {
                                val partes = listOfNotNull(
                                    passado?.let { Textos.formatar(s["te_ultimo"], "d" to vm.dataCurta(it)) },
                                    futuro?.let { Textos.formatar(s["te_agendado"], "d" to vm.dataCurta(it)) },
                                ).ifEmpty { listOf(s["te_nunca"]) }
                                Text(partes.joinToString(" · "), fontSize = 13.5.sp,
                                    color = if (recente) c.erro else if (futuro != null) c.falaBorda else c.suave,
                                    fontWeight = if (recente) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    if (aberto == n && !naoUse) {
                        val manuais = vm.datasManuais(lang, n)
                        if (manuais.isNotEmpty()) {
                            Text(s["te_datas"], color = c.suave, fontSize = 13.sp, modifier = Modifier.padding(start = 44.dp, top = 4.dp))
                            manuais.forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 44.dp)) {
                                    Text(vm.dataCurta(LocalDate.parse(d)), color = c.tinta, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { vm.apagarData(lang, n, d) }) { Text(s["te_rem"], color = c.erro) }
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 44.dp, top = 4.dp)) {
                            OutlinedButton(onClick = {
                                DatePickerDialog(ctx, { _, a, m, d -> vm.registrarData(lang, n, LocalDate.of(a, m + 1, d)) },
                                    hoje.year, hoje.monthValue - 1, hoje.dayOfMonth).show()
                            }) { Text(s["te_add"]) }
                            if (escolher != null) Button(onClick = { escolher(n); aoFechar() }) { Text(s["te_usar"]) }
                        }
                    }
                }
                HorizontalDivider(color = c.linha)
            }
        }
    }
}

/* ---------- eventos especiais (S-125, S-126) ---------- */
@Composable
fun PainelEventos(vm: AppVM, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val lang = vm.cfg.lang
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importar(uris) }
    Painel(s["ev_t"], aoFechar) {
        Text(s["ev_info"], color = c.suave, fontSize = 15.sp)
        Button(onClick = { abrir.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "*/*")) },
            modifier = Modifier.padding(vertical = 10.dp)) { Text(s["ev_importar"]) }
        for (tipo in listOf("especial", "celebracao")) {
            val ev = vm.evento(tipo, lang) ?: EventoEspecial(tipo, lang)
            EditorEvento(vm, ev, if (tipo == "especial") s["ev_esp"] else s["ev_cel"], vm.eventoCorrigido(tipo, lang))
        }
        Text(s["ev_semana_esp"], color = c.suave, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun EditorEvento(vm: AppVM, ev: EventoEspecial, nome: String, corrigido: Boolean) {
    val c = LocalCores.current
    val s = vm.tx
    var cant by remember(ev) { mutableStateOf(ev.cantico?.toString() ?: "") }
    var cantF by remember(ev) { mutableStateOf(ev.canticoFinal?.toString() ?: "") }
    var ano by remember(ev) { mutableStateOf(ev.ano?.toString() ?: "") }
    var tema by remember(ev) { mutableStateOf(ev.tema) }
    var anuncio by remember(ev) { mutableStateOf(ev.anuncio) }
    var notas by remember(ev) { mutableStateOf(ev.notas.joinToString("\n")) }
    val mudou = cant != (ev.cantico?.toString() ?: "") || cantF != (ev.canticoFinal?.toString() ?: "") || ano != (ev.ano?.toString() ?: "") ||
        tema != ev.tema || anuncio != ev.anuncio || notas != ev.notas.joinToString("\n")
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, c.linha, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(nome, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.tinta)
        Text(Textos.formatar(s["ev_origem"], "o" to (if (corrigido) s["ev_editado"] + " · " else "") + ev.origem.ifBlank { "—" }),
            color = c.suave, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            fun soNum(v: String) = v.filter { it.isDigit() }.take(4)
            OutlinedTextField(cant, { cant = soNum(it).take(3) }, label = { Text(s["ev_cantico"]) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp),
                supportingText = { cant.toIntOrNull()?.let { vm.tituloCantico(ev.idioma, it) }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } })
            if (ev.tipo == "celebracao") OutlinedTextField(cantF, { cantF = soNum(it).take(3) }, label = { Text(s["ev_canticoF"]) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp),
                supportingText = { cantF.toIntOrNull()?.let { vm.tituloCantico(ev.idioma, it) }?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } })
            else OutlinedTextField(ano, { ano = soNum(it) }, label = { Text(s["ev_ano"]) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp))
        }
        OutlinedTextField(tema, { tema = it }, label = { Text(s["ev_tema"]) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(anuncio, { anuncio = it }, label = { Text(s["ev_anuncio"]) }, minLines = 4,
            supportingText = { Text(s["ev_anuncio_info"]) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        OutlinedTextField(notas, { notas = it }, label = { Text(s["ev_notas"]) }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(enabled = mudou, onClick = {
                vm.salvarEvento(ev.copy(cantico = cant.toIntOrNull(), canticoFinal = cantF.toIntOrNull(), ano = ano.toIntOrNull(),
                    tema = tema.trim(), anuncio = anuncio.trim(), notas = notas.lines().map { it.trim() }.filter { it.isNotEmpty() }))
            }) { Text(s["salvar"]) }
            if (corrigido) OutlinedButton(onClick = { vm.restaurarEvento(ev.tipo, ev.idioma) }) { Text(s["ev_restaurar"], color = c.erro) }
        }
    }
}

/* ---------- introdução (boas-vindas) e anúncios ---------- */
@Composable
fun CartaoIntro(vm: AppVM, reu: AppVM.Reuniao, ed: Edicao, aoModelos: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val compacto = LocalCompacto.current
    val e = reu.estado
    val mods = vm.modelos(reu.idioma, reu.tipo)
    val bruta = vm.introBruta(reu.tipo, reu.idioma, e)
    var aberto by rememberSaveable(reu.id) { mutableStateOf(reu.tipo == "cel") }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(c.cartao, RoundedCornerShape(14.dp))
            .border(1.dp, c.linha, RoundedCornerShape(14.dp))
            .padding(horizontal = if (compacto) 12.dp else 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable { aberto = !aberto }, verticalAlignment = Alignment.CenterVertically) {
            Text((if (aberto) "▾  " else "▸  ") + (if (reu.tipo == "meio") s["intro_t"] else s["intro_av_t"]).uppercase(),
                fontSize = 13.sp, color = c.suave, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.weight(1f))
            val i = mods.indexOf(bruta)
            if (i >= 0) Text(Textos.formatar(s["intro_modelo"], "n" to i + 1), fontSize = 13.sp, color = c.falaBorda, fontWeight = FontWeight.SemiBold)
            else Text("✎", fontSize = 15.sp, color = c.falaBorda)
        }
        if (!aberto) {
            Text(vm.introTexto(reu.tipo, reu.idioma, e, reu.inicio), color = c.suave, fontSize = 14.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).clickable { aberto = true })
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            mods.forEachIndexed { i, m ->
                FilterChip(selected = bruta == m, onClick = { ed.valor(CamposFds.INTRO, if (i == 0) "" else m) },
                    label = { Text(Textos.formatar(s["intro_modelo"], "n" to i + 1)) })
            }
            TextButton(onClick = aoModelos) { Text(s["intro_editar"]) }
        }
        OutlinedTextField(
            value = bruta,
            onValueChange = { v -> ed.valor(CamposFds.INTRO, if (v == mods.firstOrNull()) "" else v) },
            label = { Text(s["intro_campo"]) }, minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Text(s["intro_ajuda"], color = c.suave, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (reu.tipo != "meio") OutlinedTextField(
            value = e.valor(CamposFds.AVISOS), onValueChange = { ed.valor(CamposFds.AVISOS, it) },
            label = { Text(if (reu.tipo == "cel") s["f_avisos_cel"] else s["f_avisos"]) }, minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

/* ---------- modelos de introdução (editar os 3 modelos de cada reunião) ---------- */
@Composable
fun PainelModelos(vm: AppVM, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val lang = vm.cfg.lang
    var tipo by rememberSaveable { mutableStateOf(vm.modo) }
    Painel(s["mod_t"], aoFechar) {
        Text(s["mod_info"], color = c.suave, fontSize = 15.sp)
        Text(s["intro_ajuda"], color = c.suave, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.roteiro.presidente.Produto.modos.size > 1) for ((t, rot) in listOf("meio" to s["aba_meio"], "fds" to s["aba_fds"], "cel" to s["aba_cel"]).filter { it.first in app.roteiro.presidente.Produto.modos })
                FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(rot) })
        }
        val mods = vm.modelos(lang, tipo)
        mods.forEachIndexed { i, m ->
            var txt by remember(tipo, lang, m) { mutableStateOf(m) }
            val editado = vm.modeloEditado(lang, tipo, i)
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, c.linha, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Textos.formatar(s["intro_modelo"], "n" to i + 1), fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.weight(1f))
                    if (editado) Text(s["mod_editado"], color = c.falaBorda, fontSize = 13.sp)
                }
                OutlinedTextField(txt, { txt = it }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(enabled = txt.trim() != m && txt.isNotBlank(), onClick = { vm.salvarModelo(lang, tipo, i, txt) }) { Text(s["salvar"]) }
                    if (editado) OutlinedButton(onClick = { vm.salvarModelo(lang, tipo, i, "") }) { Text(s["mod_restaurar"], color = c.erro) }
                }
            }
        }
    }
}
