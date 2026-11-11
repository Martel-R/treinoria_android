package br.com.raevan.treinoria.nucleo.notificacoes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import br.com.raevan.treinoria.MainActivity
import br.com.raevan.treinoria.apresentacao.navegacao.DestinoNavegacao
import br.com.raevan.treinoria.dados.remoto.dto.SessaoTreinoDto
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.roundToInt

data class OpcaoAcessivelPreTreino(
    val titulo: String,
    val ingredientes: String,
    val custoMedio: String,
    val macrosResumo: String
)

data class CronogramaInteligentePreTreino(
    val horaMediaTreino: Int,
    val minutoMedioTreino: Int,
    val horarioMedioTreinoFormatado: String,
    val horaLembreteComer: Int,
    val minutoLembreteComer: Int,
    val horarioLembreteComerFormatado: String,
    val sessoesAnalisadasUltimaSemana: Int,
    val opcoesAcessiveis: List<OpcaoAcessivelPreTreino>
)

object GerenciadorNotificacoes {
    const val CANAL_TREINO_ID = "treinoria_descanso_canal"
    const val CANAL_NUTRI_PRE_TREINO_ID = "treinoria_nutri_pre_treino_canal"

    val OPCOES_ACESSIVEIS_PRE_TREINO = listOf(
        OpcaoAcessivelPreTreino(
            titulo = "🍌 Opção 1: Banana Amassada + Aveia + Café",
            ingredientes = "2 bananas prata + 3 colheres de aveia + 1 xícara de café preto (ou 2 ovos cozidos)",
            custoMedio = "~R$ 2,50",
            macrosResumo = "340 kcal • 58g Carbo • 12g Prot"
        ),
        OpcaoAcessivelPreTreino(
            titulo = "🍞 Opção 2: Pão com Ovos Mexidos + Fruta",
            ingredientes = "2 fatias de pão integral/francês + 2 ovos mexidos + 1 banana ou maçã",
            custoMedio = "~R$ 3,20",
            macrosResumo = "390 kcal • 44g Carbo • 19g Prot"
        ),
        OpcaoAcessivelPreTreino(
            titulo = "🥞 Opção 3: Crepioca Simples ou Tapioca c/ Frango/Ovo",
            ingredientes = "2 colheres de goma de tapioca + 2 ovos (ou 80g de frango desfiado)",
            custoMedio = "~R$ 3,50",
            macrosResumo = "360 kcal • 38g Carbo • 22g Prot"
        ),
        OpcaoAcessivelPreTreino(
            titulo = "🍠 Opção 4: Raiz Brasileira (Cuscuz ou Batata-Doce + Ovos)",
            ingredientes = "130g de cuscuz de milho ou batata-doce cozida + 2 ovos cozidos/mexidos",
            custoMedio = "~R$ 2,90",
            macrosResumo = "375 kcal • 48g Carbo • 17g Prot"
        )
    )

    fun inicializarCanais(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val canalDescanso = NotificationChannel(
                CANAL_TREINO_ID,
                "Cronômetro de Descanso & Treino Ativo 🏋️",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas sonoros e vibratórios ao finalizar o descanso entre séries"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 150, 400)
            }
            manager.createNotificationChannel(canalDescanso)

            val canalNutri = NotificationChannel(
                CANAL_NUTRI_PRE_TREINO_ID,
                "Lembrete Inteligente de Refeição Pré-Treino 🍌📸",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa o horário ideal para comer antes do treino com opções acessíveis e pede foto da refeição"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 120, 300)
            }
            manager.createNotificationChannel(canalNutri)
        }
    }

    /**
     * Calcula o horário médio em que o usuário treinou na última semana (ou últimas sessões)
     * e define o horário ideal para a refeição Pré-Treino (75 min antes do treino).
     */
    fun calcularCronogramaPreTreino(sessoes: List<SessaoTreinoDto>): CronogramaInteligentePreTreino {
        val zonaLocal = ZoneId.systemDefault()
        val minutosDoDiaLista = mutableListOf<Int>()

        sessoes.take(10).forEach { s ->
            try {
                val dt = OffsetDateTime.parse(s.startedAt).atZoneSameInstant(zonaLocal)
                minutosDoDiaLista.add(dt.hour * 60 + dt.minute)
            } catch (_: Exception) {}
        }

        val mediaMinutosTreino = if (minutosDoDiaLista.isNotEmpty()) {
            minutosDoDiaLista.average().roundToInt().coerceIn(360, 1380) // entre 06:00 e 23:00
        } else {
            18 * 60 // Padrão 18:00 se ainda não tiver sessões
        }

        val horaTreino = mediaMinutosTreino / 60
        val minTreino = mediaMinutosTreino % 60

        // Lembrete para comer: 75 minutos (1h15) antes do horário médio do treino!
        val minutosLembreteComer = (mediaMinutosTreino - 75).coerceAtLeast(5 * 60 + 30)
        val horaComer = minutosLembreteComer / 60
        val minComer = minutosLembreteComer % 60

        return CronogramaInteligentePreTreino(
            horaMediaTreino = horaTreino,
            minutoMedioTreino = minTreino,
            horarioMedioTreinoFormatado = String.format("%02d:%02d", horaTreino, minTreino),
            horaLembreteComer = horaComer,
            minutoLembreteComer = minComer,
            horarioLembreteComerFormatado = String.format("%02d:%02d", horaComer, minComer),
            sessoesAnalisadasUltimaSemana = minutosDoDiaLista.size,
            opcoesAcessiveis = OPCOES_ACESSIVEIS_PRE_TREINO
        )
    }

    /**
     * Agenda no AlarmManager do Android o lembrete diário no horário calculado de Pré-Treino.
     */
    fun agendarLembreteDiarioPreTreino(
        context: Context,
        sessoes: List<SessaoTreinoDto>
    ): CronogramaInteligentePreTreino {
        val cronograma = calcularCronogramaPreTreino(sessoes)
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReceptorAlarmePreTreino::class.java).apply {
                putExtra("horario_medio_treino", cronograma.horarioMedioTreinoFormatado)
                putExtra("horario_refeicao", cronograma.horarioLembreteComerFormatado)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                4040,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendario = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, cronograma.horaLembreteComer)
                set(Calendar.MINUTE, cronograma.minutoLembreteComer)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendario.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Throwable) {}
        return cronograma
    }

    /**
     * Dispara imediatamente a notificação rica de Pré-Treino com opções acessíveis
     * e botão direto para abrir a câmera/scanner e enviar a foto da refeição de pré-treino!
     */
    fun dispararNotificacaoRefeicaoPreTreino(
        context: Context,
        horarioMedioTreino: String,
        horarioRefeicao: String
    ) {
        try {
            inicializarCanais(context)
            vibrarFimDescanso(context)

            val intentAbrirScanner = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("rota_inicial", DestinoNavegacao.NutricaoEvolucao.rota)
                putExtra("abrir_camera_pre_treino", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                4041,
                intentAbrirScanner,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val textoExpandido =
                "Seu horário médio de treino na última semana é às $horarioMedioTreino! " +
                    "Coma agora ($horarioRefeicao) uma destas opções acessíveis:\n" +
                    "• 🍌 2 Bananas + 3 col. Aveia + Café (~R$ 2,50)\n" +
                    "• 🍞 2 fatias de Pão + 2 Ovos mexidos (~R$ 3,20)\n" +
                    "• 🥞 Crepioca (2 ovos + tapioca) ou Cuscuz c/ Ovo (~R$ 2,90)\n\n" +
                    "📸 Toque aqui para tirar e enviar a FOTO da sua refeição de Pré-Treino!"

            val builder = NotificationCompat.Builder(context, CANAL_NUTRI_PRE_TREINO_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("🍌 Hora do Pré-Treino ($horarioRefeicao) • Envie a Foto!")
                .setContentText("Treino previsto às $horarioMedioTreino! Veja opções baratas e envie a foto do seu prato 📸")
                .setStyle(NotificationCompat.BigTextStyle().bigText(textoExpandido))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_camera,
                    "📸 Enviar Foto do Pré-Treino",
                    pendingIntent
                )

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(4042, builder.build())
        } catch (_: Throwable) {}
    }

    fun vibrarFimDescanso(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 350, 120, 350), -1)
                )
            }
        } catch (_: Throwable) {}
    }

    fun notificarFimDescanso(context: Context, nomeExercicio: String) {
        try {
            vibrarFimDescanso(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CANAL_TREINO_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🔥 Fim do Descanso! Próxima Série")
                .setContentText("Hora de atacar a próxima série de $nomeExercicio!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(2026, builder.build())
        } catch (_: Throwable) {}
    }
}
