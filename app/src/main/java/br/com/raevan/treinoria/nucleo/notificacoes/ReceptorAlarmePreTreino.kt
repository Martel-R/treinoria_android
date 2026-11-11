package br.com.raevan.treinoria.nucleo.notificacoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver que dispara pontualmente no horário calculado da refeição Pré-Treino
 * (75 minutos antes do horário médio de treino do atleta na última semana),
 * enviando a notificação com opções acessíveis do que comer e solicitando a foto do prato!
 */
class ReceptorAlarmePreTreino : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val horarioMedioTreino = intent?.getStringExtra("horario_medio_treino") ?: "18:00"
        val horarioRefeicao = intent?.getStringExtra("horario_refeicao") ?: "16:45"
        GerenciadorNotificacoes.dispararNotificacaoRefeicaoPreTreino(
            context = context,
            horarioMedioTreino = horarioMedioTreino,
            horarioRefeicao = horarioRefeicao
        )
    }
}
