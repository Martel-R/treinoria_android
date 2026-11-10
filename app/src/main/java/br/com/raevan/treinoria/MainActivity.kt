package br.com.raevan.treinoria

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.raevan.treinoria.apresentacao.navegacao.EstruturaNavegacaoPrincipal
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.tema.TemaTreinoria

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantém a tela ligada durante o treino ativo na academia (WakeLock nativo)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}

        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}

        try {
            GerenciadorNotificacoes.inicializarCanais(this)
        } catch (_: Throwable) {}

        setContent {
            TemaTreinoria {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val solicitadorNotificacao = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { }

                    LaunchedEffect(Unit) {
                        try {
                            kotlinx.coroutines.delay(1500)
                            val temPermissao = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!temPermissao) {
                                solicitadorNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } catch (_: Throwable) {}
                    }
                }

                EstruturaNavegacaoPrincipal()
            }
        }
    }
}
