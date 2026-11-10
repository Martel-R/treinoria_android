package br.com.raevan.treinoria.nucleo.notificacoes

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

object GerenciadorNotificacoes {
    const val CANAL_TREINO_ID = "treinoria_descanso_canal"

    fun inicializarCanais(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canal = NotificationChannel(
                CANAL_TREINO_ID,
                "Cronômetro de Descanso & Treino Ativo 🏋️",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas sonoros e vibratórios ao finalizar o descanso entre séries"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 150, 400)
            }
            manager.createNotificationChannel(canal)
        }
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
