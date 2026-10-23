package app.roteiro.presidente.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** Cores do roteiro (as mesmas da versão HTML), em claro e escuro. */
@Immutable
data class Cores(
    val barra: Color, val fundo: Color, val cartao: Color, val tinta: Color, val suave: Color, val linha: Color, val campo: Color,
    val tesouros: Color, val ministerio: Color, val vida: Color,
    val falaFundo: Color, val falaBorda: Color, val consFundo: Color, val consBorda: Color,
    val sala: Color, val salaFundo: Color, val sc: Color, val scFundo: Color,
    val ok: Color, val alerta: Color, val erro: Color, val escuro: Boolean,
)

val CoresClaras = Cores(
    barra = Color(0xFF1F3B4D), fundo = Color(0xFFF4F5F7), cartao = Color.White, tinta = Color(0xFF1D2430), suave = Color(0xFF5B6472),
    linha = Color(0xFFDFE3E8), campo = Color(0xFFF8F9FB),
    tesouros = Color(0xFF2F6F7A), ministerio = Color(0xFFA8740F), vida = Color(0xFFA33A2A),
    falaFundo = Color(0xFFEEF4FB), falaBorda = Color(0xFF3B6FB6), consFundo = Color(0xFFFBF6EA), consBorda = Color(0xFFEAD8AD),
    sala = Color(0xFF6A4FB3), salaFundo = Color(0xFFF3EFFB), sc = Color(0xFFB4530B), scFundo = Color(0xFFFFF3E8),
    ok = Color(0xFF2E7D4F), alerta = Color(0xFFC98A0B), erro = Color(0xFFC0392B), escuro = false,
)

val CoresEscuras = Cores(
    barra = Color(0xFF0D1A24), fundo = Color(0xFF12161C), cartao = Color(0xFF1B2129), tinta = Color(0xFFE6E9EE), suave = Color(0xFF9AA4B2),
    linha = Color(0xFF2C343F), campo = Color(0xFF232A33),
    tesouros = Color(0xFF4C9AA6), ministerio = Color(0xFFD39B2C), vida = Color(0xFFD0614F),
    falaFundo = Color(0xFF1D2A3A), falaBorda = Color(0xFF6A9BE0), consFundo = Color(0xFF2A2518), consBorda = Color(0xFF4A3F22),
    sala = Color(0xFFA48BE8), salaFundo = Color(0xFF241F33), sc = Color(0xFFF0954A), scFundo = Color(0xFF33251A),
    ok = Color(0xFF5CC48A), alerta = Color(0xFFC98A0B), erro = Color(0xFFE05A4A), escuro = true,
)

val LocalCores = staticCompositionLocalOf { CoresClaras }

/** Fatores dos 6 tamanhos de texto (Configurações → Tamanho do texto). */
val ESCALAS_FONTE = listOf(0.85f, 0.93f, 1.0f, 1.12f, 1.25f, 1.4f)

@Composable
fun TemaApp(tema: String, fonte: Int = 2, conteudo: @Composable () -> Unit) {
    val escuro = when (tema) { "escuro" -> true; "claro" -> false; else -> isSystemInDarkTheme() }
    val c = if (escuro) CoresEscuras else CoresClaras
    val esquema = if (escuro) darkColorScheme(
        primary = c.falaBorda, onPrimary = Color.Black, background = c.fundo, surface = c.cartao, onSurface = c.tinta,
        onBackground = c.tinta, surfaceVariant = c.campo, onSurfaceVariant = c.suave, outline = c.linha, secondary = c.ministerio,
    ) else lightColorScheme(
        primary = c.falaBorda, onPrimary = Color.White, background = c.fundo, surface = c.cartao, onSurface = c.tinta,
        onBackground = c.tinta, surfaceVariant = c.campo, onSurfaceVariant = c.suave, outline = Color(0xFFAEB6C2), secondary = c.ministerio,
    )
    val dens = LocalDensity.current
    val escala = ESCALAS_FONTE[fonte.coerceIn(0, ESCALAS_FONTE.size - 1)]
    CompositionLocalProvider(LocalCores provides c, LocalDensity provides Density(dens.density, dens.fontScale * escala)) {
        MaterialTheme(colorScheme = esquema, content = conteudo)
    }
}

fun corSecao(c: Cores, sec: String?): Color = when (sec) {
    "tesouros" -> c.tesouros; "ministerio" -> c.ministerio; "vida" -> c.vida; else -> c.linha
}

/** Converte texto com <b>…</b> e <i>…</i> em texto formatado. */
fun marcado(s: String, extra: SpanStyle? = null): AnnotatedString = buildAnnotatedString {
    var negrito = 0
    var italico = 0
    var i = 0
    while (i < s.length) {
        val resto = s.substring(i)
        when {
            resto.startsWith("<b>") -> { negrito++; i += 3 }
            resto.startsWith("</b>") -> { negrito = maxOf(0, negrito - 1); i += 4 }
            resto.startsWith("<i>") -> { italico++; i += 3 }
            resto.startsWith("</i>") -> { italico = maxOf(0, italico - 1); i += 4 }
            else -> {
                val estilo = SpanStyle(
                    fontWeight = if (negrito > 0) FontWeight.Bold else null,
                    fontStyle = if (italico > 0) FontStyle.Italic else null,
                )
                val ini = length
                append(s[i])
                addStyle(estilo, ini, length)
                if (extra != null) addStyle(extra, ini, length)
                i++
            }
        }
    }
}
