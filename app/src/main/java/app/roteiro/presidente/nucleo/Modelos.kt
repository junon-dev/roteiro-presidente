package app.roteiro.presidente.nucleo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Modelos de dados. O formato JSON é o mesmo da versão HTML (cópias de segurança são compatíveis). */

@Serializable
data class Licao(
    val pub: String,          // "th" ou "lmd"
    val n: Int,
    val ponto: Int? = null,
    val txt: String = "",
)

@Serializable
data class Parte(
    val n: Int,
    val titulo: String,
    val min: Int,
    val tipo: String = "",    // discurso, joias, leitura, estudante, discurso_estudante, consideracao_min, consideracao, necessidades, ebc
    val sec: String = "",     // tesouros, ministerio, vida
    val detalhe: String = "",
    val licao: Licao? = null,
    val apendiceA: Int? = null,
    val consideracao: Boolean = false,
    val passagem: String? = null,
    val materia: String? = null,
    val cena: String? = null,
    val texto: String? = null,
)

@Serializable
data class Proxima(
    val periodo: String,
    val leitura: String,
    val tema: String,
    val vida: List<String> = emptyList(),
    val ebc: String = "",
)

@Serializable
data class Semana(
    val id: String,
    val idioma: String,
    val inicio: String,       // AAAA-MM-DD (segunda-feira)
    val periodo: String,
    val leitura: String,
    @SerialName("cantico_inicial") val canticoInicial: Int? = null,
    @SerialName("cantico_meio") val canticoMeio: Int? = null,
    @SerialName("cantico_final") val canticoFinal: Int? = null,
    val partes: List<Parte> = emptyList(),
    val origem: String = "",
    val proxima: Proxima? = null,
)

@Serializable
data class Config(
    val lang: String = "pt",
    val inicio: String = "19:30",
    val abertura: Int = 5,
    val meio: Int = 4,
    val som: Boolean = true,
    val vibrar: Boolean = true,
    val tela: Boolean = true,
    val tema: String = "auto",   // auto, claro, escuro
    val fonte: Int = 2,          // tamanho do texto: 0 (menor) a 5 (maior); 2 = normal
)

/** Estado de uma semana: visita do SC, salas adicionais e valores digitados (nomes, anotações, marcações). */
data class EstadoSemana(
    val sc: Boolean = false,
    val salas: Boolean = false,
    val v: Map<String, String> = emptyMap(),
) {
    fun valor(k: String): String = v[k].orEmpty()
    fun marcado(k: String): Boolean = v[k] == "true"
}

/** Cronômetro do modo Presidir (tempos em milissegundos desde 1970). */
@Serializable
data class EstadoPresidir(
    val sem: String,
    val passo: Int = 0,
    val inicioPasso: Long = 0,
    val pausadoEm: Long? = null,
    val acumuladoPausa: Long = 0,
    val ativo: Boolean = false,
    /** Quando o presidente começou a reunião (o relógio da reunião não para). */
    val inicioReuniao: Long = 0,
)
