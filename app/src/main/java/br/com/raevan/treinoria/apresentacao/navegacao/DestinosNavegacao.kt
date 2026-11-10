package br.com.raevan.treinoria.apresentacao.navegacao

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class DestinoNavegacao(
    val rota: String,
    val titulo: String,
    val icone: ImageVector
) {
    object Login : DestinoNavegacao("login", "Login", Icons.Default.Lock)
    object DesbloqueioBiometrico : DestinoNavegacao("desbloqueio_biometrico", "Desbloqueio", Icons.Default.Fingerprint)
    object Painel : DestinoNavegacao("painel", "Início", Icons.Default.Home)
    object TreinoAtivo : DestinoNavegacao("treino_ativo", "Treinar", Icons.Default.FitnessCenter)
    object NutricaoEvolucao : DestinoNavegacao("nutricao_evolucao", "Nutri & Corpo", Icons.Default.Restaurant)
    object PersonalHub : DestinoNavegacao("personal_hub", "Fichas", Icons.Default.Assignment)

    companion object {
        val itensBarraInferior = listOf(
            Painel,
            TreinoAtivo,
            NutricaoEvolucao,
            PersonalHub
        )
    }
}
