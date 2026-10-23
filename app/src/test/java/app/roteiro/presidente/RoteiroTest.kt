package app.roteiro.presidente

import app.roteiro.presidente.nucleo.BaseLicoes
import app.roteiro.presidente.nucleo.Config
import app.roteiro.presidente.nucleo.EstadoSemana
import app.roteiro.presidente.nucleo.ItemRoteiro
import app.roteiro.presidente.nucleo.Roteiro
import app.roteiro.presidente.nucleo.Semana
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun conselhosTemPontosDaLicao() {
        for (sem in semanas) {
            val r = Roteiro.montar(sem, EstadoSemana(), Config(), licoes, null)
            for (b in r.itens.filterIsInstance<ItemRoteiro.BlocoR>().mapNotNull { it.b.conselho }) {
                if (b.licao != null) assertTrue("${sem.id} ${b.licao}", b.pontos.isNotEmpty() || b.licao.isBlank())
            }
        }
    }
}
