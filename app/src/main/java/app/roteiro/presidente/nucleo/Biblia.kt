package app.roteiro.presidente.nucleo

/** Nomes dos livros da Bíblia (Tradução do Novo Mundo) e formatação de referências. */
object Biblia {
    private val PT = ("Gênesis|Êxodo|Levítico|Números|Deuteronômio|Josué|Juízes|Rute|1 Samuel|2 Samuel|1 Reis|2 Reis|1 Crônicas|2 Crônicas|" +
        "Esdras|Neemias|Ester|Jó|Salmo|Provérbios|Eclesiastes|Cântico de Salomão|Isaías|Jeremias|Lamentações|Ezequiel|Daniel|Oseias|Joel|" +
        "Amós|Obadias|Jonas|Miqueias|Naum|Habacuque|Sofonias|Ageu|Zacarias|Malaquias|Mateus|Marcos|Lucas|João|Atos|Romanos|1 Coríntios|" +
        "2 Coríntios|Gálatas|Efésios|Filipenses|Colossenses|1 Tessalonicenses|2 Tessalonicenses|1 Timóteo|2 Timóteo|Tito|Filêmon|Hebreus|" +
        "Tiago|1 Pedro|2 Pedro|1 João|2 João|3 João|Judas|Apocalipse").split("|")
    private val ES = ("Génesis|Éxodo|Levítico|Números|Deuteronomio|Josué|Jueces|Rut|1 Samuel|2 Samuel|1 Reyes|2 Reyes|1 Crónicas|2 Crónicas|" +
        "Esdras|Nehemías|Ester|Job|Salmo|Proverbios|Eclesiastés|El Cantar de los Cantares|Isaías|Jeremías|Lamentaciones|Ezequiel|Daniel|" +
        "Oseas|Joel|Amós|Abdías|Jonás|Miqueas|Nahúm|Habacuc|Sofonías|Ageo|Zacarías|Malaquías|Mateo|Marcos|Lucas|Juan|Hechos|Romanos|" +
        "1 Corintios|2 Corintios|Gálatas|Efesios|Filipenses|Colosenses|1 Tesalonicenses|2 Tesalonicenses|1 Timoteo|2 Timoteo|Tito|Filemón|" +
        "Hebreos|Santiago|1 Pedro|2 Pedro|1 Juan|2 Juan|3 Juan|Judas|Apocalipsis").split("|")

    private val RX = Regex("""/(\d+):(\d+):(\d+)-(\d+):(\d+):(\d+)""")

    fun livro(n: Int, lang: String): String? = (if (lang == "es") ES else PT).getOrNull(n - 1)

    /**
     * Junta links "jwpub://b/NWTR/livro:cap:vers-livro:cap:vers" numa referência legível,
     * por exemplo "Apocalipse 11:15; 12:10", "Hebreus 10:7, 9" ou "Salmo 23" (salmo inteiro).
     */
    fun formatar(links: List<String>, lang: String): String {
        val sb = StringBuilder()
        var antLivro = -1
        var antCap: Int? = null
        for (l in links) {
            val g = RX.find(l)?.groupValues?.drop(1)?.map { it.toInt() } ?: continue
            val (b, c1, v1, _, c2, v2) = g
            val nome = livro(b, lang) ?: continue
            val prefixo = { if (sb.isNotEmpty()) sb.append("; "); if (b != antLivro) sb.append(nome).append(' ') }
            if (c1 == c2 && v1 == 0) {          // salmo inteiro (a partir do cabeçalho)
                prefixo(); sb.append(c1); antLivro = b; antCap = null; continue
            }
            val trecho = if (c1 == c2) (if (v1 == v2) "$v1" else "$v1-$v2") else null
            if (trecho != null && b == antLivro && c1 == antCap) sb.append(", ").append(trecho)
            else { prefixo(); sb.append(if (trecho != null) "$c1:$trecho" else "$c1:$v1-$c2:$v2") }
            antLivro = b; antCap = c2
        }
        return sb.toString()
    }

    private operator fun <T> List<T>.component6() = this[5]
}
