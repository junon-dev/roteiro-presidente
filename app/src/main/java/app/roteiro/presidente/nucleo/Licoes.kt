package app.roteiro.presidente.nucleo

import kotlinx.serialization.Serializable

/* Resumo dos pontos das lições — th (Melhore Sua Leitura e Seu Ensino) e lmd (Ame as Pessoas — Faça Discípulos).
   Os dados ficam em assets/licoes.json (mesmo conteúdo da versão HTML). */

@Serializable
data class TextoTh(val t: String, val obj: String, val p: List<String>)

@Serializable
data class LicaoTh(val pt: TextoTh, val es: TextoTh) {
    fun em(lang: String) = if (lang == "es") es else pt
}

@Serializable
data class TextoLmd(val t: String, val l: String, val p: Map<String, List<String>>)

@Serializable
data class LicaoLmd(val secao: Int, val pt: TextoLmd, val es: TextoLmd) {
    fun em(lang: String) = if (lang == "es") es else pt
}

@Serializable
data class BaseLicoes(
    val th: Map<String, LicaoTh> = emptyMap(),
    val lmd: Map<String, LicaoLmd> = emptyMap(),
    val secoesLmd: Map<String, Map<String, String>> = emptyMap(),
    val apendiceA: Map<String, String> = emptyMap(),
    val discursoLicao: Map<String, String> = emptyMap(),
) {
    fun secaoLmd(lang: String, secao: Int): String = secoesLmd[lang]?.get(secao.toString()).orEmpty()
}
