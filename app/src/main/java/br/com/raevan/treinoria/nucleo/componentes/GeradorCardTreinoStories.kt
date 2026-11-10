package br.com.raevan.treinoria.nucleo.componentes

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ResumoExercicioCard(
    val nome: String,
    val qtdSeries: Int,
    val maiorCargaKg: Double
)

/**
 * Gera um Card HD (1080x1350) estilo Strava/Hevy com os dados do treino concluído
 * e abre a gaveta nativa de compartilhamento do Android (Instagram Stories, WhatsApp, Salvar).
 */
object GeradorCardTreinoStories {

    fun gerarECompartilharCard(
        context: Context,
        nomeAtleta: String,
        tituloTreino: String,
        duracaoMinutos: Int,
        seriesConcluidas: Int,
        volumeTotalKg: Int,
        exercicios: List<ResumoExercicioCard>,
        fotoFundo: Bitmap? = null
    ) {
        try {
            val largura = 1080
            val altura = 1350
            val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 1. Fundo Escuro (ou Foto Pós-Treino com overlay escuro)
            val paintFundo = Paint(Paint.ANTI_ALIAS_FLAG)
            if (fotoFundo != null) {
                val rectDestino = Rect(0, 0, largura, altura)
                canvas.drawBitmap(fotoFundo, null, rectDestino, paintFundo)
                paintFundo.color = Color.argb(210, 9, 13, 22)
                canvas.drawRect(0f, 0f, largura.toFloat(), altura.toFloat(), paintFundo)
            } else {
                val shader = LinearGradient(
                    0f, 0f, largura.toFloat(), altura.toFloat(),
                    intArrayOf(
                        Color.rgb(9, 13, 22),
                        Color.rgb(17, 24, 39),
                        Color.rgb(6, 78, 59)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                paintFundo.shader = shader
                canvas.drawRect(0f, 0f, largura.toFloat(), altura.toFloat(), paintFundo)
            }

            val paintTexto = Paint(Paint.ANTI_ALIAS_FLAG)
            val dataHoje = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

            // 2. Topo da Marca
            paintTexto.color = Color.rgb(16, 185, 129)
            paintTexto.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paintTexto.textSize = 30f
            canvas.drawText("🏋️ TREINORIA PRO • RAEVAN TECH GROUP", 72f, 110f, paintTexto)

            // 3. Título do Treino
            paintTexto.color = Color.WHITE
            paintTexto.textSize = 54f
            val tituloCurto = if (tituloTreino.length > 28) tituloTreino.take(28) + "..." else tituloTreino
            canvas.drawText(tituloCurto, 72f, 190f, paintTexto)

            paintTexto.color = Color.rgb(148, 163, 184)
            paintTexto.textSize = 28f
            canvas.drawText("Atleta: $nomeAtleta • $dataHoje", 72f, 245f, paintTexto)

            // 4. Três Cards de KPIs (Duração, Séries, Volume Total)
            val kpis = listOf(
                Triple("DURAÇÃO", "${duracaoMinutos} min", Color.WHITE),
                Triple("SÉRIES FEITAS", "$seriesConcluidas séries", Color.rgb(52, 211, 153)),
                Triple("VOLUME TOTAL", "$volumeTotalKg kg", Color.rgb(56, 189, 248))
            )

            val paintBox = Paint(Paint.ANTI_ALIAS_FLAG)
            val paintBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.argb(95, 16, 185, 129)
            }

            kpis.forEachIndexed { i, k ->
                val x = 72f + i * 318f
                val rect = RectF(x, 300f, x + 296f, 470f)
                paintBox.color = Color.argb(220, 15, 23, 42)
                canvas.drawRoundRect(rect, 28f, 28f, paintBox)
                canvas.drawRoundRect(rect, 28f, 28f, paintBorda)

                paintTexto.color = Color.rgb(148, 163, 184)
                paintTexto.textSize = 22f
                canvas.drawText(k.first, x + 28f, 355f, paintTexto)

                paintTexto.color = k.third
                paintTexto.textSize = 42f
                canvas.drawText(k.second, x + 28f, 425f, paintTexto)
            }

            // 5. Lista de Exercícios e Cargas da Sessão
            paintTexto.color = Color.rgb(16, 185, 129)
            paintTexto.textSize = 28f
            canvas.drawText("EXERCÍCIOS & CARGAS DESTA SESSÃO", 72f, 545f, paintTexto)

            exercicios.take(7).forEachIndexed { idx, ex ->
                val y = 605f + idx * 92f
                val rectItem = RectF(72f, y - 48f, 1008f, y + 28f)
                paintBox.color = Color.argb(230, 17, 24, 39)
                canvas.drawRoundRect(rectItem, 18f, 18f, paintBox)

                paintTexto.color = Color.WHITE
                paintTexto.textSize = 27f
                val nomeLimpo = if (ex.nome.length > 30) ex.nome.take(30) + "..." else ex.nome
                canvas.drawText("${idx + 1}. $nomeLimpo", 100f, y, paintTexto)

                paintTexto.color = Color.rgb(52, 211, 153)
                paintTexto.textSize = 27f
                val pesoFmt = if (ex.maiorCargaKg % 1.0 == 0.0) ex.maiorCargaKg.toInt().toString() else ex.maiorCargaKg.toString()
                canvas.drawText("${ex.qtdSeries} séries • ${pesoFmt} kg", 700f, y, paintTexto)
            }

            // 6. Rodapé Oficial
            paintTexto.color = Color.rgb(203, 213, 225)
            paintTexto.textSize = 25f
            canvas.drawText("treinoria.raevan.com.br • Powered by Coach IA Gemini 2.5", 72f, 1285f, paintTexto)

            // Salva no cache compartilhável e abre o Share Sheet do Android
            val pasta = File(context.cacheDir, "cards_treino").apply { mkdirs() }
            val arquivo = File(pasta, "treino-treinoria-${System.currentTimeMillis()}.png")
            FileOutputStream(arquivo).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivo
            )

            val intentShare = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🏋️ Treino Concluído no TreinorIA PRO: $tituloTreino ($volumeTotalKg kg de volume total)! 🚀 https://treinoria.raevan.com.br"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intentShare, "Compartilhar Card do Treino (Stories / WhatsApp)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {}
    }
}
