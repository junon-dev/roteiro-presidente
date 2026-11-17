package app.roteiro.presidente.nucleo

/**
 * Textos da apresentação (ao abrir o app), das novidades de cada versão e da Ajuda, em PT e ES.
 *
 * Boas práticas seguidas:
 *  - apresentação curta (no máximo 5 telas), uma ideia por tela, com "Pular" sempre à vista;
 *  - a permissão da janela flutuante é pedida dentro da tela que explica para que ela serve;
 *  - novidades aparecem uma única vez por versão, só para quem atualizou, começando pela mais importante;
 *  - tudo fica disponível depois em Configurações → Ajuda e dicas.
 *
 * Para uma nova versão: acrescente um item em [novidades] com o versionCode dela (app/build.gradle).
 */
object Dicas {

    /** Uma tela da apresentação, uma novidade ou uma dica da Ajuda. */
    data class Dica(
        val id: String,
        /** livro, editar, play, janela, arquivo, novidade, relogio, palco, ajuda, app */
        val icone: String,
        val titulo: String,
        val texto: String,
        /** "idioma" (escolher o idioma) ou "permissao" (botão para permitir a janela com botões) */
        val acao: String? = null,
    )

    data class Versao(val codigo: Int, val nome: String, val nomeGuia: String, val itens: List<Dica>)

    /** produto: "meio", "guia" ou "completo" */
    fun apresentacao(lang: String, produto: String, nomeApp: String): List<Dica> {
        val es = lang == "es"
        val meio = produto != "guia"
        val fds = produto != "meio"
        val l = ArrayList<Dica>()
        l += Dica(
            "boasvindas", "app",
            if (es) "Bienvenido a $nomeApp" else "Bem-vindo ao $nomeApp",
            when (produto) {
                "guia" -> if (es) "Su guía para presidir la reunión del fin de semana, la visita del superintendente de circuito, el discurso especial y la Conmemoración. Funciona sin internet."
                          else "Seu guia para presidir a reunião do fim de semana, a visita do superintendente de circuito, o discurso especial e a Celebração. Funciona sem internet."
                "meio" -> if (es) "Su guion para presidir la reunión Vida y Ministerio: palabras sugeridas, asignados, cronómetro y elogios y consejos. Funciona sin internet."
                          else "Seu roteiro para presidir a reunião Vida e Ministério: falas sugeridas, designados, cronômetro e elogios e conselhos. Funciona sem internet."
                else -> if (es) "Guion para presidir las reuniones de entre semana y del fin de semana, con cronómetro, asignados y eventos especiales. Funciona sin internet."
                        else "Roteiro para presidir as reuniões do meio e do fim de semana, com cronômetro, designados e eventos especiais. Funciona sem internet."
            },
            acao = "idioma",
        )
        l += Dica(
            "arquivos", "arquivo",
            if (es) "Importe los archivos de jw.org" else "Importe os arquivos do jw.org",
            if (meio) {
                if (es) "En Configuración → Archivos, importe la guía de actividades (PDF), los folletos th y lmd (PDF) y el cancionero (.jwpub). La app reconoce sola el tipo y el idioma, y ya trae algunas semanas listas."
                else "Em Configurações → Arquivos, importe a apostila (PDF), as brochuras th e lmd (PDF) e o cancioneiro (.jwpub). O app reconhece sozinho o tipo e o idioma, e já vem com algumas semanas prontas."
            } else {
                if (es) "Los temas de los discursos (S-99) y los eventos ya vienen en la app. En Configuración → Archivos puede importar el cancionero (.jwpub) para ver el título de las canciones."
                else "Os temas dos discursos (S-99) e os eventos já vêm no app. Em Configurações → Arquivos você pode importar o cancioneiro (.jwpub) para ver o título dos cânticos."
            },
        )
        l += Dica(
            "preparar", "editar",
            when {
                meio && fds -> if (es) "Prepare las reuniones" else "Prepare as reuniões"
                fds -> if (es) "Prepare el fin de semana" else "Prepare o fim de semana"
                else -> if (es) "Prepare la semana" else "Prepare a semana"
            },
            when {
                meio && fds -> if (es) "Elija la reunión y la semana arriba y escriba los nombres de los asignados. En el fin de semana, el tema del discurso aparece al escribir el número (S-99)."
                               else "Escolha a reunião e a semana no topo e digite os nomes dos designados. No fim de semana, o tema do discurso aparece ao digitar o número (S-99)."
                fds -> if (es) "Escriba el orador, la congregación y el número del discurso: el tema viene de la lista S-99. En Agenda, planifique los próximos oradores y vea el historial de los temas."
                       else "Digite o orador, a congregação e o número do discurso: o tema vem da lista S-99. Na Agenda, planeje os próximos oradores e veja o histórico dos temas."
                else -> if (es) "Elija la semana arriba y escriba los nombres de los asignados. Marque la visita del superintendente de circuito o las salas auxiliares cuando las haya."
                        else "Escolha a semana no topo e digite os nomes dos designados. Marque a visita do superintendente de circuito ou as salas adicionais quando houver."
            },
        )
        l += Dica(
            "presidir", "play",
            if (es) "Presida con el cronómetro" else "Presida com o cronômetro",
            (if (es) "Toque Presidir: la app sigue cada parte, avisa cuando se acaba el tiempo y muestra si la reunión va adelantada o atrasada. El modo Plataforma agranda el texto y oculta los campos."
             else "Toque em Presidir: o app acompanha cada parte, avisa quando o tempo acaba e mostra se a reunião está adiantada ou atrasada. O modo palco aumenta o texto e esconde os campos.") +
                if (meio) (if (es) " En Lecciones están los puntos para los elogios y consejos." else " Em Lições estão os pontos para os elogios e conselhos.") else "",
        )
        l += janela(es, novidade = false)
        return l
    }

    private fun janela(es: Boolean, novidade: Boolean) = Dica(
        "janela", "janela",
        if (novidade) { if (es) "Botones siempre visibles en la ventana flotante" else "Botões sempre visíveis na janela flutuante" }
        else { if (es) "Siga fuera de la app" else "Continue fora do app" },
        if (es) "Si abre otra app durante la reunión (la Biblia, por ejemplo), la parte actual y el cronómetro siguen en una ventana flotante con los botones volver, pausar y avanzar siempre a la vista. Arrástrela para cambiarla de lugar o redúzcala a un botón pequeño. Hay que permitir una vez “Mostrar sobre otras apps”."
        else "Se abrir outro app durante a reunião (a Bíblia, por exemplo), a parte atual e o cronômetro continuam numa janela flutuante com os botões voltar, pausar e avançar sempre à vista. Arraste para mudar de lugar ou recolha num botão pequeno. É preciso permitir uma vez “Sobrepor a outros apps”.",
        acao = "permissao",
    )

    /** Novidades de cada versão, da mais nova para a mais antiga. */
    fun novidades(lang: String, produto: String): List<Versao> {
        val es = lang == "es"
        return listOf(
            Versao(
                18, "4.2", "1.2",
                listOf(
                    janela(es, novidade = true),
                    Dica(
                        "ajuda", "ajuda",
                        if (es) "Presentación y Ayuda" else "Apresentação e Ajuda",
                        if (es) "Al abrir la app, una presentación corta muestra las funciones principales (puede marcar “No mostrar más”). Véala de nuevo cuando quiera en Configuración → Ayuda y consejos, junto con consejos de uso y las novedades de cada versión."
                        else "Ao abrir o app, uma apresentação curta mostra as principais funções (você pode marcar “Não mostrar mais”). Veja de novo quando quiser em Configurações → Ajuda e dicas, junto com dicas de uso e as novidades de cada versão.",
                    ),
                ),
            ),
            Versao(
                17, "4.1", "1.1",
                listOf(
                    Dica(
                        "aovivo", "relogio",
                        if (es) "La reunión sigue fuera de la app" else "A reunião continua fora do app",
                        if (es) "Ventana flotante, cronómetro en la notificación y, en los plegables medio abiertos (modo mesa), el cronómetro arriba y los botones abajo."
                        else "Janela flutuante, cronômetro na notificação e, nos dobráveis meio abertos (modo mesa), o cronômetro em cima e os botões embaixo.",
                    ),
                ),
            ),
        )
    }

    /**
     * Novidades que ainda não foram mostradas (versões depois de [vistas] até a atual).
     * vistas = 0: instalado antes de o app registrar isso (até a 4.1) — mostra só as da versão atual.
     */
    fun pendentes(lang: String, produto: String, vistas: Int, atual: Int): List<Versao> {
        val desde = if (vistas <= 0) atual - 1 else vistas
        return novidades(lang, produto).filter { it.codigo in (desde + 1)..atual }
    }

    /** Dicas extras da Ajuda (além das telas da apresentação). */
    fun ajuda(lang: String, produto: String): List<Dica> {
        val es = lang == "es"
        val meio = produto != "guia"
        val l = ArrayList<Dica>()
        l += Dica("cores", "relogio", if (es) "Colores del cronómetro" else "Cores do cronômetro",
            if (es) "Verde: queda tiempo. Amarillo: último minuto (o el último 20 % de la parte). Rojo: tiempo excedido, con el tiempo de más."
            else "Verde: tempo sobrando. Amarelo: último minuto (ou os últimos 20% da parte). Vermelho: tempo excedido, mostrando quanto passou.")
        l += Dica("relogio", "relogio", if (es) "Reloj de la reunión" else "Relógio da reunião",
            if (es) "Si empieza tarde o por una parte del medio, la app pregunta cómo contar el tiempo de la reunión: desde la hora de inicio, por la suma de las partes anteriores o a partir de ahora."
            else "Se você começar atrasado ou por uma parte do meio, o app pergunta como contar o tempo da reunião: pelo horário de início, pela soma das partes anteriores ou a partir de agora.")
        l += Dica("janelaUso", "janela", if (es) "Ventana flotante" else "Janela flutuante",
            if (es) "Con el permiso “Mostrar sobre otras apps”, los botones quedan siempre visibles. Sin él, la app usa la ventana del propio Android: toque la ventana una vez para ver los botones. También se abre con el botón de ventana en la barra de Presidir."
            else "Com a permissão “Sobrepor a outros apps”, os botões ficam sempre visíveis. Sem ela, o app usa a janela do próprio Android: toque uma vez na janela para ver os botões. Também abre pelo botão de janela na barra do Presidir.")
        l += Dica("notif", "relogio", if (es) "Cronómetro en la notificación" else "Cronômetro na notificação",
            if (es) "Mientras preside, la parte y el tiempo aparecen en la barra de notificaciones, con los mismos botones. Se puede desactivar en Configuración."
            else "Enquanto você preside, a parte e o tempo aparecem na barra de notificações, com os mesmos botões. Pode ser desligado em Configurações.")
        l += Dica("mesa", "palco", if (es) "Plegables" else "Dobráveis",
            if (es) "Con el teléfono medio abierto en horizontal (modo mesa), el cronómetro queda arriba y los botones abajo."
            else "Com o celular meio aberto na horizontal (modo mesa), o cronômetro fica em cima e os botões embaixo.")
        if (meio) l += Dica("licoes", "livro", if (es) "Elogios y consejos" else "Elogios e conselhos",
            if (es) "En Lecciones están los puntos de los folletos th y lmd para elogiar y aconsejar al estudiante después de cada parte."
            else "Em Lições estão os pontos das brochuras th e lmd para elogiar e aconselhar o estudante depois de cada parte.")
        l += Dica("relatorio", "editar", if (es) "Informe de la reunión" else "Relatório da reunião",
            if (es) "Al salir del modo Presidir, el informe muestra el tiempo real de cada parte y se puede compartir."
            else "Ao sair do modo Presidir, o relatório mostra o tempo real de cada parte e pode ser compartilhado.")
        l += Dica("backup", "arquivo", if (es) "Copia de seguridad" else "Cópia de segurança",
            if (es) "En Configuración, exporte los datos o elija una carpeta (puede ser de Google Drive) para una copia automática al cerrar la app."
            else "Em Configurações, exporte os dados ou escolha uma pasta (pode ser do Google Drive) para uma cópia automática ao fechar o app.")
        return l
    }
}
