package app.roteiro.presidente

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.test.core.app.ApplicationProvider
import app.roteiro.presidente.ui.PainelConfig
import app.roteiro.presidente.ui.PainelRelatorio
import app.roteiro.presidente.ui.TelaPrincipal
import app.roteiro.presidente.ui.TemaApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Gera imagens das telas para conferência visual (pasta app/build/telas).
 * Celular: 411 x 891 dp · Tablet: 1280 x 800 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
abstract class TelasBase(private val prefixo: String) {
    @get:Rule val regra = createComposeRule()
    private val dir = File("build/telas").apply { mkdirs() }

    // captura a tela inteira, inclusive janelas de diálogo (painéis)
    private fun foto(nome: String) {
        regra.waitForIdle()
        captureScreenRoboImage(File(dir, "$prefixo-$nome.png").path)
    }
    private fun fotoDialogo(nome: String) = foto(nome)

    @Test
    fun telas() {
        val vm = AppVM(ApplicationProvider.getApplicationContext<Application>()).apply { relogioAutomatico = false }
        vm.irParaSemana("pt-2026-10-19")
        regra.setContent { TemaApp(vm.cfg.tema, vm.cfg.fonte) { TelaPrincipal(vm) } }
        foto("1-roteiro")

        // modo Presidir na parte 4 (estudante), com modo palco
        val passos = vm.roteiro(vm.semanaAtual()!!).passos
        regra.runOnIdle { vm.iniciarPresidir(passos.size) }
        regra.runOnIdle { vm.irPasso(passos.indexOfFirst { it.bloco == "p4" }, passos.size) }
        regra.runOnIdle { vm.agora = System.currentTimeMillis() + 150_000 }   // simula 2:30 de parte
        foto("2-presidir-palco")
        regra.runOnIdle { vm.palco = false }
        foto("3-presidir-edicao")
        regra.runOnIdle { vm.irPasso(passos.indexOfFirst { it.bloco == "p4" && it.cons }, passos.size) }
        regra.runOnIdle { vm.pararPresidir() }
        foto("4-depois")
    }

    @Test
    fun paineis() {
        val vm = AppVM(ApplicationProvider.getApplicationContext<Application>()).apply { relogioAutomatico = false }
        vm.irParaSemana("es-2026-10-19")
        vm.mudarIdioma("es")
        val passos = vm.roteiro(vm.semanaAtual()!!).passos
        vm.iniciarPresidir(passos.size); vm.irPasso(3, passos.size); vm.pararPresidir()
        regra.setContent {
            TemaApp(vm.cfg.tema, vm.cfg.fonte) {
                androidx.compose.runtime.CompositionLocalProvider(app.roteiro.presidente.ui.LocalCompacto provides (prefixo == "celular")) {
                    PainelRelatorio(vm, vm.semanaAtual()!!) {}
                }
            }
        }
        fotoDialogo("5-relatorio")
    }

    @Test
    fun configuracoes() {
        val vm = AppVM(ApplicationProvider.getApplicationContext<Application>()).apply { relogioAutomatico = false }
        regra.setContent {
            TemaApp(vm.cfg.tema, vm.cfg.fonte) {
                androidx.compose.runtime.CompositionLocalProvider(app.roteiro.presidente.ui.LocalCompacto provides (prefixo == "celular")) {
                    PainelConfig(vm, aoCanticos = {}) {}
                }
            }
        }
        fotoDialogo("6-configuracoes")
    }
}

@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class TelasCelularTest : TelasBase("celular")

@Config(qualifiers = "w1280dp-h800dp-land-mdpi")
class TelasTabletTest : TelasBase("tablet")
