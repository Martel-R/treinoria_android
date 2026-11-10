package br.com.raevan.treinoria.apresentacao.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.seguranca.GerenciadorBiometria
import br.com.raevan.treinoria.nucleo.tema.VerdeEsmeralda

@Composable
fun TelaDesbloqueioBiometrico(
    aoDesbloquearComSucesso: () -> Unit,
    aoEntrarComSenha: () -> Unit
) {
    val contexto = LocalContext.current
    val activity = contexto as? FragmentActivity
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    val ultimoEmail = remember {
        try { GerenciadorSessao.obterInstancia().obterUltimoEmail() } catch (_: Exception) { "" }
    }

    val dispararBiometria = {
        try {
            if (activity != null && GerenciadorBiometria.podeAutenticarComBiometria(contexto)) {
                mensagemErro = null
                GerenciadorBiometria.solicitarAutenticacao(
                    activity = activity,
                    titulo = "Desbloquear TreinorIA",
                    subtitulo = "Confirme sua digital ou reconhecimento facial",
                    aoSucesso = { aoDesbloquearComSucesso() },
                    aoErro = { erro -> mensagemErro = erro }
                )
            } else {
                aoDesbloquearComSucesso()
            }
        } catch (t: Throwable) {
            mensagemErro = t.localizedMessage ?: "Use sua senha para entrar."
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        dispararBiometria()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(VerdeEsmeralda.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "TreinorIA",
                    tint = VerdeEsmeralda,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TREINORIA PRO",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = VerdeEsmeralda
            )

            Text(
                text = if (ultimoEmail.isNotBlank()) "Sessão ativa: $ultimoEmail" else "Sessão protegida por biometria",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (mensagemErro != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mensagemErro!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { dispararBiometria() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Desbloquear com Biometria", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = aoEntrarComSenha,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Entrar com E-mail e Senha")
            }
        }
    }
}
