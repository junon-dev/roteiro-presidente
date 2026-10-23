@file:OptIn(ExperimentalLayoutApi::class)

package app.roteiro.presidente.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.roteiro.presidente.AppVM
import app.roteiro.presidente.BuildConfig
import app.roteiro.presidente.nucleo.Textos
import java.time.LocalDate

/* ---------- moldura comum dos painéis ---------- */
@Composable
fun Painel(titulo: String, aoFechar: () -> Unit, rolavel: Boolean = true, conteudo: @Composable ColumnScope.() -> Unit) {
    val c = LocalCores.current
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = c.cartao,
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.95f).fillMaxHeight(0.92f).imePadding(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.weight(1f))
                    IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, null) }
                }
                if (rolavel) Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp), content = conteudo)
                else Column(Modifier.padding(bottom = 12.dp), content = conteudo)
            }
        }
    }
}

@Composable
private fun Secao(t: String) {
    val c = LocalCores.current
    Text(t.uppercase(), color = c.suave, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
private fun Linha(rotulo: String, detalhe: String? = null, controle: @Composable () -> Unit) {
    val c = LocalCores.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, fontWeight = FontWeight.SemiBold, color = c.tinta)
            detalhe?.let { Text(it, color = c.suave, fontSize = 13.5.sp) }
        }
        controle()
    }
    HorizontalDivider(color = c.linha)
}

@Composable
private fun Opcoes(opcoes: List<Pair<String, String>>, atual: String, aoEscolher: (String) -> Unit) {
    val c = LocalCores.current
    Row(Modifier.border(1.dp, c.linha, RoundedCornerShape(10.dp))) {
        opcoes.forEach { (v, rot) ->
            val on = v == atual
            Text(rot, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else c.tinta,
                modifier = Modifier.background(if (on) c.falaBorda else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { aoEscolher(v) }.padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun Contador(valor: Int, min: Int, max: Int, aoMudar: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { if (valor > min) aoMudar(valor - 1) }) { Text("−") }
        Text("$valor", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { if (valor < max) aoMudar(valor + 1) }) { Text("+") }
    }
}

/* ---------- Configurações (inclui os arquivos) ---------- */
@Composable
fun PainelConfig(vm: AppVM, aoCanticos: () -> Unit, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val ctx = LocalContext.current
    var confirmarLimpar by remember { mutableStateOf(false) }

    val abrirArquivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importar(uris) }
    val salvarBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportarTexto().toByteArray()) } }.isSuccess
            vm.resultado = if (ok) s["salvo"] else s["salvoErro"]
        }
    }
    val abrirBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            vm.resultado = if (t != null && vm.importarTexto(t)) s["importado"] else s["imp_erro"]
        }
    }

    Painel(s["config"], aoFechar) {
        Secao(s["geral"])
        Linha(s["idioma"]) { Opcoes(listOf("pt" to "Português", "es" to "Español"), vm.cfg.lang) { vm.mudarIdioma(it) } }
        Linha(s["horaIni"]) {
            TextButton(onClick = {
                val m = app.roteiro.presidente.nucleo.Roteiro.minDe(vm.cfg.inicio)
                android.app.TimePickerDialog(ctx, { _, h, mi -> vm.mudarCfg { it.copy(inicio = "%02d:%02d".format(h, mi)) } }, m / 60, m % 60, true).show()
            }) { Text(vm.cfg.inicio, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        Linha(s["abertura"]) { Contador(vm.cfg.abertura, 1, 10) { v -> vm.mudarCfg { it.copy(abertura = v) } } }
        Linha(s["canticoMeio"]) { Contador(vm.cfg.meio, 1, 10) { v -> vm.mudarCfg { it.copy(meio = v) } } }
        Linha(s["som"]) { Switch(vm.cfg.som, { on -> vm.mudarCfg { it.copy(som = on) } }) }
        Linha(s["vibrar"]) { Switch(vm.cfg.vibrar, { on -> vm.mudarCfg { it.copy(vibrar = on) } }) }
        Linha(s["tela"]) { Switch(vm.cfg.tela, { on -> vm.mudarCfg { it.copy(tela = on) } }) }
        Linha(s["tema"]) { Opcoes(listOf("auto" to s["temaAuto"], "claro" to s["temaClaro"], "escuro" to s["temaEscuro"]), vm.cfg.tema) { t -> vm.mudarCfg { it.copy(tema = t) } } }

        // Arquivos
        Secao(s["arquivos"])
        Text(s["arq_info"], color = c.suave, fontSize = 15.sp)
        Button(onClick = { abrirArquivos.launch(arrayOf("application/pdf", "application/octet-stream", "application/zip", "*/*")) },
            modifier = Modifier.padding(vertical = 10.dp)) { Text(s["arq_importar"]) }

        Text(s["arq_apostilas"], fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.padding(top = 8.dp))
        val grupos = vm.todasSemanas().groupBy { it.idioma to it.origem.ifBlank { "—" } }
        if (grupos.isEmpty()) Text(s["nenhumArquivo"], color = c.suave)
        grupos.forEach { (chave, ws) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Textos.NOME_IDIOMA[chave.first]} · ${ws.first().periodo} → ${ws.last().periodo}", fontWeight = FontWeight.SemiBold, color = c.tinta)
                    Text("${ws.size} ${s["semanas"]} · ${chave.second} · ${if (ws.all { vm.ehEmbutida(it.id) }) s["ap_emb"] else s["ap_imp"]}", color = c.suave, fontSize = 13.5.sp)
                }
                TextButton(onClick = { vm.removerSemanas(ws.map { it.id }) }) { Text(s["ap_rem"], color = c.erro) }
            }
            HorizontalDivider(color = c.linha)
        }

        Text(s["arq_brochuras"], fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.padding(top = 14.dp))
        for (pub in listOf("th", "lmd")) for (lang in listOf("pt", "es")) {
            val n = vm.qtdOficiais(pub, lang)
            val importado = vm.oficiaisImportados(pub, lang)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (pub == "th") s["lic_th"] else s["lic_lmd"]} · ${Textos.NOME_IDIOMA[lang]}", color = c.tinta, modifier = Modifier.weight(1f))
                if (n > 0) {
                    Text("✓ $n ${s["licoes"].lowercase()} · ${if (importado) s["ap_imp"] else s["ap_emb"]}", color = c.ok, fontWeight = FontWeight.SemiBold)
                    if (importado) TextButton(onClick = { vm.removerOficiais(pub, lang) }) { Text(s["ap_rem"], color = c.erro) }
                } else Text(s["br_nenhuma"], color = c.suave)
            }
        }

        Text(s["canticos"], fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("pt", "es").joinToString(" · ") { "${Textos.NOME_IDIOMA[it]}: ${vm.canticos(it).size}" },
                color = c.suave, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = aoCanticos) { Text(s["verEditar"]) }
        }

        // Cópia de segurança
        Secao(s["backup"])
        Text(s["backupInfo"], color = c.suave, fontSize = 15.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { salvarBackup.launch("roteiro-presidente-${LocalDate.now()}.json") }) { Text(s["exportar"]) }
            OutlinedButton(onClick = { abrirBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(s["importarBk"]) }
            OutlinedButton(onClick = { confirmarLimpar = true }) { Text(s["limparSem"], color = c.erro) }
        }

        Secao(s["sobre"])
        Text(s["sobre_txt"], color = c.suave, fontSize = 15.sp)
        Text("${s["versao"]} ${BuildConfig.VERSION_NAME}", color = c.suave, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }

    if (confirmarLimpar) AlertDialog(
        onDismissRequest = { confirmarLimpar = false },
        text = { Text(s["confLimpar"]) },
        confirmButton = { TextButton(onClick = { vm.limparSemana(); confirmarLimpar = false; vm.resultado = s["limpo"] }) { Text(s["confirmar"]) } },
        dismissButton = { TextButton(onClick = { confirmarLimpar = false }) { Text(s["cancelar"]) } },
    )
}

/* ---------- Cânticos ---------- */
@Composable
fun PainelCanticos(vm: AppVM, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    var lang by rememberSaveable { mutableStateOf(vm.cfg.lang) }
    var num by rememberSaveable { mutableStateOf("") }
    var tit by rememberSaveable { mutableStateOf("") }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importar(uris) }
    val lista = vm.canticos(lang).entries.sortedBy { it.key.toIntOrNull() ?: 0 }
    Painel(s["canticos"], aoFechar, rolavel = false) {
        Text(s["can_info"], color = c.suave, fontSize = 15.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            Opcoes(listOf("pt" to "Português", "es" to "Español"), lang) { lang = it }
            Button(onClick = { abrir.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }) { Text(s["can_importar"]) }
        }
        Text(s["can_adicionar"], fontWeight = FontWeight.Bold, color = c.tinta)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            OutlinedTextField(num, { v -> num = v.filter { it.isDigit() }.take(3); vm.tituloCantico(lang, num.toIntOrNull() ?: -1)?.let { tit = it } },
                label = { Text(s["can_numero"]) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(tit, { tit = it }, label = { Text(s["can_titulo"]) }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { num.toIntOrNull()?.let { vm.definirCantico(lang, it, tit); num = ""; tit = "" } }) { Text(s["salvar"]) }
        }
        if (lista.isEmpty()) Text(Textos.formatar(s["can_vazio"], "i" to Textos.NOME_IDIOMA[lang]), color = c.suave, modifier = Modifier.padding(top = 10.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(lista, key = { it.key }) { (n, t) ->
                Row(Modifier.fillMaxWidth().clickable { num = n; tit = t }.padding(vertical = 7.dp)) {
                    Text(n, fontWeight = FontWeight.Bold, color = c.tinta, modifier = Modifier.width(48.dp))
                    Text(t, color = c.tinta, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = c.linha)
            }
        }
        if (vm.dados.canticos[lang].orEmpty().isNotEmpty()) TextButton(onClick = { vm.limparCanticos(lang) }) {
            Text(Textos.formatar(s["can_limpar"], "i" to Textos.NOME_IDIOMA[lang]), color = c.erro)
        }
    }
}

/* ---------- Lições ---------- */
@Composable
fun PainelLicoes(vm: AppVM, aoFechar: () -> Unit) {
    val c = LocalCores.current
    val s = vm.tx
    val lang = vm.cfg.lang
    var aba by rememberSaveable { mutableStateOf("th") }
    Painel(s["lic_t"], aoFechar) {
        Row(Modifier.padding(bottom = 10.dp)) {
            Opcoes(listOf("th" to s["lic_th"], "lmd" to s["lic_lmd"]), aba) { aba = it }
        }
        if (lang == "es" && vm.qtdOficiais(aba, "es") == 0) Text(s["lic_prov"], color = c.suave, fontSize = 14.sp)
        val cartao = Modifier.fillMaxWidth().padding(vertical = 5.dp).border(1.dp, c.linha, RoundedCornerShape(10.dp)).padding(12.dp)
        if (aba == "th") {
            vm.licoes.th.entries.sortedBy { it.key.toInt() }.forEach { (n, d) ->
                val t = d.em(lang)
                val o = vm.oficial("th", lang, n.toInt())
                Column(cartao) {
                    Text("$n. ${o?.titulo ?: t.t}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.tinta)
                    Text(o?.objetivo ?: t.obj, color = c.suave, fontSize = 14.5.sp)
                    t.p.forEachIndexed { i, x ->
                        val tp = o?.pontos?.get(i.toString())
                        Text(marcado("•  " + (tp?.let { "<b>$it.</b> " } ?: "") + x), color = c.tinta, fontSize = 15.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        } else {
            vm.licoes.lmd.entries.sortedBy { it.key.toInt() }.forEach { (n, d) ->
                val t = d.em(lang)
                val o = vm.oficial("lmd", lang, n.toInt())
                Column(cartao) {
                    Text("$n. ${o?.titulo ?: t.t} · ${vm.licoes.secaoLmd(lang, d.secao)}", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.tinta)
                    Text(marcado("<b>${s["aprendemos"]}:</b> ${o?.objetivo ?: t.l}"), color = c.suave, fontSize = 14.5.sp)
                    t.p.entries.sortedBy { it.key.toInt() }.forEach { (k, v) ->
                        val nome = o?.pontos?.get(k) ?: v.getOrElse(0) { "" }
                        Text(marcado("<b>${s["ponto"]} $k — $nome.</b> ${v.getOrElse(1) { "" }}"), color = c.tinta, fontSize = 15.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}
