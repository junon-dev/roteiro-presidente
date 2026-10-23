package app.roteiro.presidente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.roteiro.presidente.dados.Migracao
import app.roteiro.presidente.ui.TelaPrincipal
import app.roteiro.presidente.ui.TemaApp

class MainActivity : ComponentActivity() {

    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Primeira abertura depois de atualizar da versão 1 (HTML): recupera nomes, anotações e apostilas.
        if (savedInstanceState == null && !vm.repo.existe()) {
            val mig = Migracao(this, vm.repo.json)
            if (mig.temVersaoAnterior()) {
                mig.executar { a ->
                    if (a != null) { vm.substituir(a); vm.avisar(vm.tx["migrado"]) }
                    else vm.gravarAgora()
                }
            } else vm.gravarAgora()
        }

        setContent {
            TemaApp(vm.cfg.tema) { TelaPrincipal(vm) }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.gravarAgora()
    }
}
