package br.com.raevan.treinoria.apresentacao.navegacao

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.raevan.treinoria.apresentacao.login.LoginViewModel
import br.com.raevan.treinoria.apresentacao.login.TelaDesbloqueioBiometrico
import br.com.raevan.treinoria.apresentacao.login.TelaLogin
import br.com.raevan.treinoria.apresentacao.nutricao.TelaNutricaoEvolucao
import br.com.raevan.treinoria.apresentacao.painel.TelaPainelPrincipal
import br.com.raevan.treinoria.apresentacao.personal.TelaPersonalEConstrutor
import br.com.raevan.treinoria.apresentacao.treino.TelaTreinoAtivo
import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.atualizacao.DialogoAtualizacaoApp
import br.com.raevan.treinoria.nucleo.atualizacao.GerenciadorAtualizacaoApp
import br.com.raevan.treinoria.nucleo.atualizacao.InfoVersaoRemota
import br.com.raevan.treinoria.nucleo.tema.SuperficieCard
import br.com.raevan.treinoria.nucleo.tema.VerdeEsmeralda
import io.github.jan.supabase.auth.auth

@Composable
fun EstruturaNavegacaoPrincipal() {
    val controladorNavegacao = rememberNavController()
    val entradaPilhaAtual by controladorNavegacao.currentBackStackEntryAsState()
    val rotaAtual = entradaPilhaAtual?.destination?.route

    val contexto = LocalContext.current
    val (versaoInstaladaCodigo, versaoInstaladaNome) = remember {
        GerenciadorAtualizacaoApp.obterVersaoInstalada(contexto)
    }
    var infoAtualizacaoPendente by remember { mutableStateOf<InfoVersaoRemota?>(null) }
    var fichaAtivaSelecionada by remember { mutableStateOf<FichaTreinoDto?>(null) }
    var abrirCameraPreTreinoAutomatico by remember {
        mutableStateOf(
            try {
                (contexto as? android.app.Activity)?.intent?.getBooleanExtra("abrir_camera_pre_treino", false) ?: false
            } catch (_: Throwable) {
                false
            }
        )
    }
    var roleUsuario by remember {
        mutableStateOf(
            try { GerenciadorSessao.obterInstancia().obterRoleSalva() } catch (_: Exception) { "athlete" }
        )
    }

    LaunchedEffect(rotaAtual) {
        try {
            val p = RepositorioTreinoria.carregarPerfil()
            if (p != null) {
                roleUsuario = p.accountRole
            }
        } catch (_: Throwable) {}
    }

    LaunchedEffect(Unit) {
        try {
            val disponivel = GerenciadorAtualizacaoApp.verificarAtualizacaoDisponivel(contexto)
            if (disponivel != null) {
                infoAtualizacaoPendente = disponivel
            }
        } catch (_: Throwable) {}
    }

    if (infoAtualizacaoPendente != null) {
        DialogoAtualizacaoApp(
            infoRemota = infoAtualizacaoPendente!!,
            versaoInstaladaNome = versaoInstaladaNome,
            versaoInstaladaCodigo = versaoInstaladaCodigo,
            aoFechar = { infoAtualizacaoPendente = null }
        )
    }

    val loginViewModel: LoginViewModel = viewModel()
    val sessaoExiste = remember {
        try {
            GerenciadorSessao.obterInstancia().temSessaoSalva() ||
                ClienteSupabase.client.auth.currentSessionOrNull() != null
        } catch (_: Throwable) {
            false
        }
    }

    val rotaExtra = remember {
        try {
            (contexto as? android.app.Activity)?.intent?.getStringExtra("rota_inicial")
        } catch (_: Throwable) {
            null
        }
    }

    val rotaInicial = when {
        !rotaExtra.isNullOrBlank() -> rotaExtra
        sessaoExiste -> DestinoNavegacao.DesbloqueioBiometrico.rota
        else -> DestinoNavegacao.Login.rota
    }

    val ehPersonal = roleUsuario.lowercase() in listOf("trainer", "gym", "admin")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (rotaAtual != DestinoNavegacao.Login.rota && rotaAtual != DestinoNavegacao.DesbloqueioBiometrico.rota) {
                NavigationBar(
                    containerColor = SuperficieCard
                ) {
                    DestinoNavegacao.itensBarraInferior.forEach { destino ->
                        val estaSelecionado = rotaAtual == destino.rota
                        val tituloAba = if (destino == DestinoNavegacao.PersonalHub) {
                            if (ehPersonal) "Personal" else "Desempenho"
                        } else {
                            destino.titulo
                        }
                        val iconeAba = if (destino == DestinoNavegacao.PersonalHub) {
                            if (ehPersonal) Icons.Default.Groups else Icons.Default.TrendingUp
                        } else {
                            destino.icone
                        }

                        NavigationBarItem(
                            selected = estaSelecionado,
                            onClick = {
                                if (rotaAtual != destino.rota) {
                                    controladorNavegacao.navigate(destino.rota) {
                                        popUpTo(controladorNavegacao.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = iconeAba,
                                    contentDescription = tituloAba
                                )
                            },
                            label = {
                                Text(
                                    text = tituloAba,
                                    fontSize = 10.sp,
                                    fontWeight = if (estaSelecionado) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = VerdeEsmeralda,
                                selectedTextColor = VerdeEsmeralda,
                                indicatorColor = VerdeEsmeralda.copy(alpha = 0.16f)
                            )
                        )
                    }
                }
            }
        }
    ) { preenchimentoInterno ->
        NavHost(
            navController = controladorNavegacao,
            startDestination = rotaInicial,
            modifier = Modifier
                .fillMaxSize()
                .padding(preenchimentoInterno)
        ) {
            composable(DestinoNavegacao.Login.rota) {
                TelaLogin(
                    viewModel = loginViewModel,
                    aoLoginSucesso = {
                        controladorNavegacao.navigate(DestinoNavegacao.Painel.rota) {
                            popUpTo(DestinoNavegacao.Login.rota) { inclusive = true }
                        }
                    }
                )
            }

            composable(DestinoNavegacao.DesbloqueioBiometrico.rota) {
                TelaDesbloqueioBiometrico(
                    aoDesbloquearComSucesso = {
                        controladorNavegacao.navigate(DestinoNavegacao.Painel.rota) {
                            popUpTo(DestinoNavegacao.DesbloqueioBiometrico.rota) { inclusive = true }
                        }
                    },
                    aoEntrarComSenha = {
                        loginViewModel.fazerLogout()
                        controladorNavegacao.navigate(DestinoNavegacao.Login.rota) {
                            popUpTo(DestinoNavegacao.DesbloqueioBiometrico.rota) { inclusive = true }
                        }
                    }
                )
            }

            composable(DestinoNavegacao.Painel.rota) {
                TelaPainelPrincipal(
                    aoIniciarTreinoDaFicha = { ficha ->
                        fichaAtivaSelecionada = ficha
                        controladorNavegacao.navigate(DestinoNavegacao.TreinoAtivo.rota)
                    },
                    aoAbrirPersonalHub = {
                        controladorNavegacao.navigate(DestinoNavegacao.PersonalHub.rota)
                    },
                    aoAbrirScannerPreTreino = {
                        abrirCameraPreTreinoAutomatico = true
                        controladorNavegacao.navigate(DestinoNavegacao.NutricaoEvolucao.rota) {
                            popUpTo(controladorNavegacao.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    aoFazerLogout = {
                        loginViewModel.fazerLogout()
                        controladorNavegacao.navigate(DestinoNavegacao.Login.rota) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(DestinoNavegacao.TreinoAtivo.rota) {
                TelaTreinoAtivo(
                    fichaSelecionada = fichaAtivaSelecionada,
                    aoConcluirTreino = {
                        controladorNavegacao.navigate(DestinoNavegacao.Painel.rota) {
                            popUpTo(DestinoNavegacao.Painel.rota) { inclusive = true }
                        }
                    }
                )
            }

            composable(DestinoNavegacao.NutricaoEvolucao.rota) {
                TelaNutricaoEvolucao(
                    iniciarCameraPreTreino = abrirCameraPreTreinoAutomatico,
                    aoConsumirGatilhoCamera = { abrirCameraPreTreinoAutomatico = false }
                )
            }

            composable(DestinoNavegacao.PersonalHub.rota) {
                TelaPersonalEConstrutor()
            }
        }
    }
}
