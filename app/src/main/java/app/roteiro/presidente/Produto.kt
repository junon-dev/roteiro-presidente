package app.roteiro.presidente

/** Qual dos três apps é este (definido na compilação: meio, guia ou completo). */
object Produto {
    val id: String = BuildConfig.PRODUTO
    /** Reuniões que aparecem neste app. */
    val modos: List<String> = when (id) {
        "meio" -> listOf("meio")
        "guia" -> listOf("fds", "cel")
        else -> listOf("meio", "fds", "cel")
    }
    /** Agenda de oradores e histórico dos temas (só no Guia do Presidente). */
    val agenda: Boolean = id == "guia"
    val temFds: Boolean = "fds" in modos || "cel" in modos
    val temMeio: Boolean = "meio" in modos
}
