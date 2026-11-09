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
            "[saudação]! Sejam todos muito bem-vindos. Ficamos muito felizes com a presença de cada um, especialmente de quem está aqui pela primeira vez. Estamos reunidos para lembrar a morte de Jesus Cristo, assim como ele pediu na noite antes de morrer: “Persistam em fazer isso em memória de mim.” Hoje, milhões de pessoas no mundo inteiro fazem o mesmo, lembrando o maior gesto de amor que já existiu: Jesus deu a sua vida por nós. O discurso vai nos ajudar a mostrar gratidão a Jeová e a Jesus por esse presente tão valioso.",
            "[saudação]. (PAUSA) Damos as boas-vindas a todos os presentes. Neste dia tão especial, milhões de pessoas no mundo inteiro estão reunidas para lembrar a ocasião mais importante do ano: a Ceia do Senhor. A congregação [congregação] tem muita alegria em receber vocês para esta importante reunião anual.",
            "[saudação]! Sejam todos muito bem-vindos à Celebração da morte de Jesus Cristo, também chamada de Ceia do Senhor. Se é a primeira vez que você assiste a esta celebração, ficamos muito contentes por você estar aqui. Durante a reunião, o pão e o vinho serão passados, porque representam o corpo e o sangue que Jesus deu por nós. O discurso vai explicar por que essa morte é tão importante e como ela mostra o grande amor de Jeová e de Jesus por cada um de nós.",
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
            "¡[saludo]! Les damos a todos una cordial bienvenida. Nos alegra mucho que estén aquí, sobre todo quienes vienen por primera vez. Estamos reunidos para recordar la muerte de Jesucristo, tal como él pidió la noche antes de morir: “Sigan haciendo esto en memoria de mí”. Hoy, millones de personas en todo el mundo hacen lo mismo y recuerdan el mayor acto de amor de la historia: Jesús dio su vida por nosotros. El discurso nos ayudará a mostrar gratitud a Jehová y a Jesús por un regalo tan valioso.",
            "[saludo]. (PAUSA) Les extendemos una cordial bienvenida a todos los presentes. En este día tan especial, millones de personas alrededor del mundo estamos reunidos para recordar la ocasión más importante del año: “la Cena del Señor”. La congregación [congregación] se complace en reunirse con ustedes para esta importante reunión anual.",
            "¡[saludo]! Bienvenidos a la Conmemoración de la muerte de Jesucristo, también llamada la Cena del Señor. Si es la primera vez que asiste a esta celebración, nos alegra mucho que esté aquí. Durante la reunión se pasarán el pan y el vino, porque representan el cuerpo y la sangre que Jesús dio por nosotros. El discurso explicará por qué su muerte es tan importante y cómo muestra el gran amor que Jehová y Jesús nos tienen.",
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
