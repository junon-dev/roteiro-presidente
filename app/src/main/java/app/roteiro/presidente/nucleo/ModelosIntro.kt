package app.roteiro.presidente.nucleo

/**
 * Modelos de introdução (boas-vindas) que o presidente escolhe e edita na preparação.
 * Marcadores trocados automaticamente:
 *   [saudação] / [saludo]      → Bom dia / Boa tarde / Boa noite (pelo horário)
 *   [tema]                     → tema do discurso
 *   [orador] / [discursante]   → nome do orador
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
            "[saudação]! Sejam todos muito bem-vindos. Estamos reunidos para a Celebração da morte de Jesus Cristo, também chamada de Ceia do Senhor. Esta é a única celebração anual que Jesus mandou seus seguidores fazerem. Na noite antes de morrer, ele disse: “Persistam em fazer isso em memória de mim.” (Lucas 22:19) Por isso, hoje milhões de pessoas no mundo inteiro estão reunidas, como nós, para lembrar o que Jesus fez por nós.",
            "[saudação] a todos! Que alegria ver tantas pessoas aqui. Há cerca de dois mil anos, nesta mesma data do calendário bíblico, 14 de nisã, Jesus passou a última noite com os apóstolos e deu início a esta celebração. Hoje vamos lembrar o grande amor que Jeová e Jesus mostraram por nós, e ficamos muito felizes por vocês estarem aqui conosco.",
            "[saudação]! Sejam muito bem-vindos. Esta noite vamos lembrar o maior presente que Deus nos deu: o resgate. Jesus deu a sua vida por nós. O pão e o vinho que serão passados representam o corpo e o sangue de Jesus, e o discurso vai explicar o que o sacrifício dele pode significar para cada um de nós. Agradecemos de coração a presença de todos.",
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
            "¡[saludo]! Les damos la más cordial bienvenida. Estamos reunidos para la Conmemoración de la muerte de Jesucristo, también llamada la Cena del Señor. Es la única celebración anual que Jesús mandó a sus seguidores observar. La noche antes de morir, dijo: “Sigan haciendo esto en memoria de mí” (Lucas 22:19). Por eso, hoy millones de personas en todo el mundo están reunidas, como nosotros, para recordar lo que Jesús hizo por nosotros.",
            "¡[saludo] a todos! Qué alegría ver a tantas personas aquí. Hace unos dos mil años, en esta misma fecha del calendario bíblico, el 14 de nisán, Jesús pasó su última noche con los apóstoles y dio inicio a esta celebración. Hoy recordaremos el gran amor que Jehová y Jesús han mostrado por nosotros, y nos alegra mucho que nos acompañen.",
            "¡[saludo]! Bienvenidos. Esta noche recordaremos el mayor regalo que Dios nos ha dado: el rescate. Jesús dio su vida por nosotros. El pan y el vino que se pasarán representan el cuerpo y la sangre de Jesús, y el discurso explicará lo que su sacrificio puede significar para cada uno de nosotros. Les agradecemos de corazón su presencia.",
        ),
    )

    fun padrao(lang: String, tipo: String): List<String> = (if (lang == "es") es else pt)[tipo].orEmpty()

    /** Troca os marcadores pelo texto da reunião. */
    fun preencher(texto: String, saudacao: String, tema: String, orador: String): String {
        var t = texto
        for (k in listOf("[saudação]", "[saudacao]", "[saludo]")) t = t.replace(k, saudacao, ignoreCase = true)
        t = t.replace("[tema]", tema.ifBlank { "________" }, ignoreCase = true)
        for (k in listOf("[orador]", "[discursante]")) t = t.replace(k, orador.ifBlank { "________" }, ignoreCase = true)
        return t.trim()
    }
}
