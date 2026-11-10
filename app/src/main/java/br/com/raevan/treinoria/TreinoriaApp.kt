package br.com.raevan.treinoria

import android.app.Application
import android.util.Log
import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes

class TreinoriaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            GerenciadorSessao.inicializar(this)
        } catch (t: Throwable) {
            Log.e("TreinoriaApp", "Erro ao inicializar GerenciadorSessao", t)
        }

        try {
            ClienteSupabase.inicializar(this)
        } catch (t: Throwable) {
            Log.e("TreinoriaApp", "Erro ao inicializar ClienteSupabase", t)
        }

        try {
            GerenciadorNotificacoes.inicializarCanais(this)
        } catch (t: Throwable) {
            Log.e("TreinoriaApp", "Erro ao inicializar canais de notificação", t)
        }
    }
}
