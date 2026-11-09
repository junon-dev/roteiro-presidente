package app.roteiro.presidente.nucleo

/**
 * Modelos de introdução (boas-vindas) que o presidente escolhe e edita na preparação.
 * Marcadores trocados automaticamente:
 *   [saudação] / [saludo]      → Bom dia / Boa tarde / Boa noite (pelo horário)
 *   [tema]                     → tema do discurso
 *   [orador] / [discursante]   → nome do orador
 *   [congregação] / [congregación] → nome da congregação (Configurações)
 */
object ModelosIntro {
    val TIPOS = listOf("meio", "fds", "cel")

    private val pt = mapOf(
        "meio" to listOf(
            "[saudação], irmãos e amigos! Sejam todos bem-vindos à reunião Nossa Vida e Ministério Cristão. Que bom ter vocês aqui!",
            "[saudação] a todos! É uma alegria estarmos juntos mais uma vez. Damos as boas-vindas de modo especial a quem está nos visitando. Nesta reunião, vamos aprender da Bíblia coisas práticas para o nosso ministério e para a nossa vida cristã.",
            "[saudação]! Sejam muito bem-vindos. Esta semana temos um programa muito bom preparado para nós. Vamos prestar bastante atenção e participar com entusiasmo.",
        ),
        "fds" to listOf(
            "[saudação], irmãos e amigos! Sejam todos bem-vindos à nossa reunião. Ficamos muito felizes com a presença de vocês.",
            "[saudação] a todos! Damos as boas-vindas de modo especial a quem está aqui pela primeira vez. Esperamos que se sintam em casa e que gostem do que vamos aprender da Bíblia hoje.",
            "[saudação]! Sejam muito bem-vindos. Hoje vamos ouvir o discurso “[tema]” e depois teremos o Estudo de A Sentinela. Com certeza vamos aprender muito da Palavra de Deus.",
        ),
        "cel" to listOf(
            "Sejam todos muito bem-vindos. Ficamos muito felizes com a presença de cada um, especialmente de quem está aqui pela primeira vez. Estamos reunidos para lembrar a morte de Jesus Cristo, assim como ele pediu na noite antes de morrer: “Persistam em fazer isso em memória de mim.” Hoje, milhões de pessoas no mundo inteiro fazem o mesmo, lembrando o maior gesto de amor que já existiu. O discurso a seguir vai nos ajudar a entender por que a morte de Jesus foi tão importante e como podemos nos beneficiar desse ato de amor que Jeová e Jesus fizeram por nós.",
            "Damos as boas-vindas a todos os presentes. Neste dia tão especial, milhões de pessoas no mundo inteiro estão reunidas para lembrar a ocasião mais importante do ano: a Ceia do Senhor. A congregação [congregação] tem muita alegria em receber vocês para esta importante reunião anual.",
            "É uma alegria receber todos vocês, especialmente quem nos visita pela primeira vez. Esta noite atendemos a um pedido que Jesus fez aos seus seguidores na noite antes de morrer: “Persistam em fazer isso em memória de mim.” Por isso, neste mesmo dia, pessoas no mundo inteiro estão reunidas para lembrar a morte dele. O discurso a seguir vai nos mostrar por que esse acontecimento é tão importante e o que ele pode significar para cada um de nós.",
        ),
    )

    private val es = mapOf(
        "meio" to listOf(
            "¡[saludo], hermanos y amigos! Bienvenidos a la reunión Vida y Ministerio Cristianos. ¡Qué bueno tenerlos aquí!",
            "¡[saludo] a todos! Es una alegría estar juntos una vez más. Les damos una bienvenida especial a quienes nos visitan. En esta reunión aprenderemos de la Biblia cosas prácticas para nuestro ministerio y nuestra vida cristiana.",
            "¡[saludo]! Bienvenidos. Esta semana tenemos un programa muy bueno preparado para nosotros. Prestemos mucha atención y participemos con entusiasmo.",
        ),
        "fds" to listOf(
            "¡[saludo], hermanos y amigos! Bienvenidos a nuestra reunión. Nos alegra mucho que estén aquí.",
            "¡[saludo] a todos! Les damos una bienvenida especial a quienes vienen por primera vez. Esperamos que se sientan como en casa y que disfruten lo que vamos a aprender de la Biblia hoy.",
            "¡[saludo]! Bienvenidos. Hoy escucharemos el discurso «[tema]» y después tendremos el Estudio de La Atalaya. Sin duda aprenderemos mucho de la Palabra de Dios.",
        ),
        "cel" to listOf(
            "Les damos a todos una cordial bienvenida. Nos alegra mucho que estén aquí, sobre todo quienes vienen por primera vez. Estamos reunidos para recordar la muerte de Jesucristo, tal como él pidió la noche antes de morir: “Sigan haciendo esto en memoria de mí”. Hoy, millones de personas en todo el mundo hacen lo mismo y recuerdan el mayor acto de amor de la historia. El discurso que escucharemos a continuación nos ayudará a entender por qué la muerte de Jesús fue tan importante y cómo podemos beneficiarnos de este acto de amor que Jehová y Jesús hicieron por nosotros.",
            "Les extendemos una cordial bienvenida a todos los presentes. En este día tan especial, millones de personas alrededor del mundo estamos reunidos para recordar la ocasión más importante del año: “la Cena del Señor”. La congregación [congregación] se complace en reunirse con ustedes para esta importante reunión anual.",
            "Es una alegría recibirlos a todos, en especial a quienes nos visitan por primera vez. Esta noche cumplimos con algo que Jesús pidió a sus seguidores la noche antes de morir: “Sigan haciendo esto en memoria de mí”. Por eso, en este mismo día, personas en todo el mundo están reunidas para recordar su muerte. El discurso que escucharemos a continuación nos mostrará por qué este acontecimiento es tan importante y lo que puede significar para cada uno de nosotros.",
        ),
    )

    fun padrao(lang: String, tipo: String): List<String> = (if (lang == "es") es else pt)[tipo].orEmpty()

    /** Troca os marcadores pelo texto da reunião. */
    fun preencher(texto: String, saudacao: String, tema: String, orador: String, congregacao: String = ""): String {
        var t = texto
        for (k in listOf("[congregação]", "[congregacao]", "[congregación]", "[congregacion]")) t = t.replace(k, congregacao.ifBlank { "________" }, ignoreCase = true)
        for (k in listOf("[saudação]", "[saudacao]", "[saludo]")) t = t.replace(k, saudacao, ignoreCase = true)
        t = t.replace("[tema]", tema.ifBlank { "________" }, ignoreCase = true)
        for (k in listOf("[orador]", "[discursante]")) t = t.replace(k, orador.ifBlank { "________" }, ignoreCase = true)
        return t.trim()
    }
}
