package app.roteiro.presidente

import app.roteiro.presidente.nucleo.BaseLicoes
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.ItemRoteiro
import app.roteiro.presidente.nucleo.Roteiro
import app.roteiro.presidente.nucleo.Semana
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import app.roteiro.presidente.nucleo.Seg
import org.junit.Test
import java.io.File

/** Confere, para todas as semanas incluídas no app, que o roteiro fecha em 1h45 (normal e com visita do SC). */
class RoteiroTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val semanas = json.decodeFromString(ListSerializer(Semana.serializer()), File("src/main/assets/semanas.json").readText())
    private val licoes = json.decodeFromString(BaseLicoes.serializer(), File("src/main/assets/licoes.json").readText())

    @Test
    fun todasAsSemanasFechamEm105Minutos() {
        assertTrue(semanas.size >= 20)
        for (sem in semanas) for (sc in listOf(false, true)) for (salas in listOf(false, true)) {
            val r = Roteiro.montar(sem, EstadoSemana(sc = sc, salas = salas), Config(), licoes, sem.proxima)
            assertEquals("${sem.id} sc=$sc", 105, r.passos.sumOf { it.dur })
            assertEquals("21:15", r.fim)
            val estudantes = r.itens.filterIsInstance<ItemRoteiro.BlocoR>().count { it.b.conselho != null }
            assertTrue("${sem.id}: partes de estudante", estudantes >= 3)
        }
    }

    @Test
    fun titulosIncluidosNoApp() {
        val can = json.decodeFromString(MapSerializer(String.serializer(),
            MapSerializer(String.serializer(), String.serializer())),
            File("src/main/assets/canticos.json").readText())
        assertTrue((can["pt"]?.size ?: 0) >= 150 && (can["es"]?.size ?: 0) >= 150)
        val of = json.decodeFromString(MapSerializer(String.serializer(),
            MapSerializer(String.serializer(),
                MapSerializer(String.serializer(), app.roteiro.presidente.nucleo.LicaoOficial.serializer()))),
            File("src/main/assets/oficiais.json").readText())
        for (lang in listOf("pt", "es")) {
            assertEquals(20, of["th"]?.get(lang)?.size)
            assertEquals(12, of["lmd"]?.get(lang)?.size)
        }
    }

    @Test
    fun canticosAnunciadosComTextoBase() {
        val tx = json.decodeFromString(MapSerializer(String.serializer(),
            MapSerializer(String.serializer(), String.serializer())),
            File("src/main/assets/textos_canticos.json").readText())
        assertTrue((tx["pt"]?.size ?: 0) >= 150 && (tx["es"]?.size ?: 0) >= 150)
        assertEquals("Apocalipse 4:11", tx["pt"]?.get("1"))
        assertEquals("Apocalipsis 4:11", tx["es"]?.get("1"))
        for (sem in semanas) {
            val r = Roteiro.montar(sem, EstadoSemana(), Config(), licoes, null,
                tituloCantico = { n -> "Título $n" }, textoCantico = { n -> tx[sem.idioma]?.get(n.toString()) })
            val blocos = r.itens.filterIsInstance<ItemRoteiro.BlocoR>().map { it.b }.associateBy { it.id }
            for ((id, n) in listOf("abertura" to sem.canticoInicial, "meio" to sem.canticoMeio, "fim" to sem.canticoFinal)) {
                if (n == null) continue
                val base = tx[sem.idioma]?.get(n.toString()) ?: continue
                val fala = blocos[id]!!.falas.flatten().filterIsInstance<Seg.Txt>().joinToString("") { it.s }
                assertTrue("${sem.id} $id: $fala", fala.contains(base) && fala.contains("Título $n"))
                assertTrue("${sem.id} $id subtítulo", blocos[id]!!.subtitulo!!.contains(base))
            }
        }
    }

    @Test
    fun fimDeSemanaECelebracao() {
        val ev = json.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(),
            app.roteiro.presidente.nucleo.EventoEspecial.serializer())), File("src/main/assets/eventos.json").readText())
        val disc = json.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
            File("src/main/assets/discursos.json").readText())
        assertEquals(194, disc["pt"]?.size)
        for (lang in listOf("pt", "es")) {
            val esp = ev["especial"]!![lang]!!
            val cel = ev["celebracao"]!![lang]!!
            assertEquals(146, esp.cantico); assertEquals(25, cel.cantico); assertEquals(18, cel.canticoFinal)
            assertTrue(esp.anuncio.contains("[") && cel.anuncio.contains("["))
            for (sc in listOf(false, true)) for (especial in listOf(false, true)) {
                val e = EstadoSemana(sc = sc, v = if (especial) mapOf("especial" to "true") else emptyMap())
                val r = app.roteiro.presidente.nucleo.RoteiroFds.montar(lang, e, "09:00", esp, null)
                assertEquals("$lang sc=$sc esp=$especial", 105, r.passos.sumOf { it.dur })
                assertEquals("10:45", r.fim)
            }
            val c = app.roteiro.presidente.nucleo.RoteiroFds.montarCelebracao(lang, EstadoSemana(), "19:30", cel)
            assertEquals(58, c.passos.sumOf { it.dur })
            val anuncio = c.itens.filterIsInstance<ItemRoteiro.BlocoR>().first { it.b.id == "anuncio" }.b
            assertTrue(anuncio.campos.any { it.k == "anuncio.0" })
        }
    }

    @Test
    fun conselhosTemPontosDaLicao() {
        for (sem in semanas) {
            val r = Roteiro.montar(sem, EstadoSemana(), Config(), licoes, null)
            for (b in r.itens.filterIsInstance<ItemRoteiro.BlocoR>().mapNotNull { it.b.conselho }) {
                if (b.licao != null) assertTrue("${sem.id} ${b.licao}", b.pontos.isNotEmpty() || b.licao.isBlank())
            }
        }
    }

    /** Apresentação curta (até 5 telas), em PT e ES, nos três apps; novidades só das versões ainda não vistas. */
    @Test
    fun apresentacaoENovidades() {
        for (lang in listOf("pt", "es")) for (prod in listOf("meio", "guia", "completo")) {
            val a = app.roteiro.presidente.nucleo.Dicas.apresentacao(lang, prod, "App")
            assertTrue("$lang/$prod: ${a.size} telas", a.size in 3..5)
            assertTrue(a.all { it.titulo.isNotBlank() && it.texto.isNotBlank() })
            assertTrue("permissão da janela na apresentação", a.any { it.acao == "permissao" })
            assertTrue(app.roteiro.presidente.nucleo.Dicas.ajuda(lang, prod).isNotEmpty())
        }
        val d = app.roteiro.presidente.nucleo.Dicas
        assertEquals(listOf(18), d.pendentes("pt", "meio", 17, 18).map { it.codigo })
        assertEquals(listOf(18), d.pendentes("pt", "meio", 0, 18).map { it.codigo })   // atualizou da 4.1
        assertTrue(d.pendentes("pt", "meio", 18, 18).isEmpty())
        // a versão atual precisa ter as novidades escritas (Dicas.novidades)
        assertTrue("faltam as novidades da versão ${BuildConfig.VERSION_CODE} em Dicas.kt",
            d.novidades("pt", "meio").any { it.codigo == BuildConfig.VERSION_CODE })
        for (k in listOf("jcCfg", "jc_info", "jc_falta", "jc_permitir", "jc_recolher", "jc_abrirApp", "jc_expandir", "ajuda", "ajuda_abrir",
            "ajuda_mostrar", "guia_pular", "guia_proximo", "guia_comecar", "guia_naoMostrar", "guia_novidades", "guia_pagina"))
            for (l in listOf("pt", "es")) assertTrue("$l: $k", app.roteiro.presidente.nucleo.Textos.de(l)[k] != k)
    }
}
