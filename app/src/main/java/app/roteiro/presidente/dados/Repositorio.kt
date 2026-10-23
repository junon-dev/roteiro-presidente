package app.roteiro.presidente.dados

import android.content.Context
import app.roteiro.presidente.nucleo.BaseLicoes
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.EstadoPresidir
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.LicaoOficial
import app.roteiro.presidente.nucleo.Semana
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File

/** Estado de uma semana como fica gravado (compatível com a versão HTML: marcações são true/false). */
@Serializable
data class EstadoSemanaDto(
    val sc: Boolean = false,
    val salas: Boolean = false,
    val v: Map<String, JsonElement> = emptyMap(),
) {
    fun paraModelo() = EstadoSemana(sc, salas, v.mapValues { (_, e) ->
        val p = e as? JsonPrimitive
        when {
            p == null -> ""
            p.isString -> p.content
            p.booleanOrNull != null -> p.booleanOrNull.toString()
            else -> p.contentOrNull.orEmpty()
        }
    })

    companion object {
        fun de(e: EstadoSemana) = EstadoSemanaDto(e.sc, e.salas, e.v.mapValues { (k, valor) ->
            if (k.startsWith("chk.")) JsonPrimitive(valor == "true") else JsonPrimitive(valor)
        })
    }
}

/** Tudo o que o app guarda. Também é o formato da cópia de segurança (Exportar/Importar dados). */
@Serializable
data class Arquivo(
    val versao: Int = 2,
    val cfg: Config = Config(),
    val importadas: List<Semana> = emptyList(),
    val removidas: List<String> = emptyList(),
    val semanas: Map<String, EstadoSemanaDto> = emptyMap(),
    val presidir: EstadoPresidir? = null,
    /** idioma -> (número -> título) */
    val canticos: Map<String, Map<String, String>> = emptyMap(),
    /** "th"/"lmd" -> idioma -> número -> títulos oficiais */
    val oficiais: Map<String, Map<String, Map<String, LicaoOficial>>> = emptyMap(),
)

class Repositorio(private val ctx: Context) {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    private val arq = File(ctx.filesDir, "dados.json")

    fun existe(): Boolean = arq.exists()

    fun carregar(): Arquivo = try {
        if (arq.exists()) json.decodeFromString(Arquivo.serializer(), arq.readText()) else Arquivo()
    } catch (e: Exception) {
        // arquivo corrompido: guarda uma cópia e começa do zero
        arq.copyTo(File(ctx.filesDir, "dados-corrompido-${System.currentTimeMillis()}.json"), overwrite = true)
        Arquivo()
    }

    @Synchronized
    fun gravar(a: Arquivo) {
        val tmp = File(ctx.filesDir, "dados.json.tmp")
        tmp.writeText(json.encodeToString(Arquivo.serializer(), a))
        if (!tmp.renameTo(arq)) { arq.delete(); tmp.renameTo(arq) }
    }

    fun paraTexto(a: Arquivo): String = json.encodeToString(Arquivo.serializer(), a)

    fun deTexto(t: String): Arquivo = json.decodeFromString(Arquivo.serializer(), t)

    fun semanasEmbutidas(): List<Semana> = try {
        ctx.assets.open("semanas.json").bufferedReader().use { json.decodeFromString(ListSerializer(Semana.serializer()), it.readText()) }
    } catch (e: Exception) { emptyList() }

    fun licoes(): BaseLicoes = try {
        ctx.assets.open("licoes.json").bufferedReader().use { json.decodeFromString(BaseLicoes.serializer(), it.readText()) }
    } catch (e: Exception) { BaseLicoes() }
}
