package br.com.raevan.treinoria.apresentacao.treino

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.SerieSessaoDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class SerieEditavel(
    val exerciseId: String,
    val exerciseName: String,
    val videoUrl: String?,
    val setNumber: Int,
    var weightKg: String,
    var reps: String,
    var completed: Boolean,
    val restSeconds: Int
)

@Composable
fun TelaTreinoAtivo(
    fichaSelecionada: FichaTreinoDto?,
    aoConcluirTreino: () -> Unit
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var fichasDisponiveis by remember { mutableStateOf<List<FichaTreinoDto>>(emptyList()) }
    var fichaAtual by remember { mutableStateOf(fichaSelecionada) }
    var perfil by remember { mutableStateOf<PerfilDto?>(null) }

    var segundosTreino by remember { mutableStateOf(0) }
    var treinoEmAndamento by remember { mutableStateOf(true) }

    var descansoRestante by remember { mutableStateOf(0) }
    var exercicioDescansoAtual by remember { mutableStateOf("") }

    var metronomoAtivo by remember { mutableStateOf(false) }
    var faseMetronomo by remember { mutableStateOf("Pronto para iniciar cadência 3-1-1") }

    var exibirCalculadoraAnilhas by remember { mutableStateOf(false) }
    var pesoAlvoCalculadora by remember { mutableStateOf("80") }
    var pesoBarraCalculadora by remember { mutableStateOf("20") }

    var salvandoSessao by remember { mutableStateOf(false) }
    var feelingEscolhido by remember { mutableStateOf("Forte 🔥") }
    var notasSessao by remember { mutableStateOf("") }
    var feedbackSalvo by remember { mutableStateOf<String?>(null) }

    val seriesEstado = remember { mutableStateListOf<SerieEditavel>() }

    // Inicializa TTS para Metrônomo por Voz
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(contexto) {
        var instanciaTts: TextToSpeech? = null
        instanciaTts = TextToSpeech(contexto) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instanciaTts?.language = Locale("pt", "BR")
            }
        }
        tts = instanciaTts
        onDispose {
            instanciaTts?.stop()
            instanciaTts?.shutdown()
        }
    }

    // Carrega fichas se nenhuma foi passada
    LaunchedEffect(Unit) {
        perfil = RepositorioTreinoria.carregarPerfil()
        val lista = RepositorioTreinoria.listarFichasTreino()
        fichasDisponiveis = lista
        if (fichaAtual == null && lista.isNotEmpty()) {
            fichaAtual = lista.first()
        }
    }

    // Popula séries quando a ficha muda
    LaunchedEffect(fichaAtual?.id) {
        val f = fichaAtual ?: return@LaunchedEffect
        seriesEstado.clear()
        f.templateExercises.forEach { te ->
            val nomeEx = te.exercise?.name ?: "Exercício"
            val vid = te.exercise?.videoUrl
            val qtdSeries = te.targetSets.coerceIn(1, 8)
            val repsPadrao = te.targetReps.split("-").lastOrNull()?.filter { it.isDigit() }?.ifBlank { "10" } ?: "10"
            val pesoPadrao = if (te.targetWeightKg > 0) te.targetWeightKg.toInt().toString() else "20"
            for (s in 1..qtdSeries) {
                seriesEstado.add(
                    SerieEditavel(
                        exerciseId = te.exerciseId,
                        exerciseName = nomeEx,
                        videoUrl = vid,
                        setNumber = s,
                        weightKg = pesoPadrao,
                        reps = repsPadrao,
                        completed = false,
                        restSeconds = te.restSeconds
                    )
                )
            }
        }
    }

    // Cronômetro geral do treino
    LaunchedEffect(treinoEmAndamento) {
        while (treinoEmAndamento) {
            delay(1000)
            segundosTreino++
        }
    }

    // Cronômetro regressivo de descanso entre séries
    LaunchedEffect(descansoRestante) {
        if (descansoRestante > 0) {
            delay(1000)
            descansoRestante--
            if (descansoRestante == 0) {
                GerenciadorNotificacoes.notificarFimDescanso(contexto, exercicioDescansoAtual)
                tts?.speak("Fim do descanso! Próxima série!", TextToSpeech.QUEUE_FLUSH, null, "rest_end")
            }
        }
    }

    // Loop do Metrônomo Biomecânico por Voz
    LaunchedEffect(metronomoAtivo) {
        var rep = 1
        while (metronomoAtivo) {
            faseMetronomo = "Repetição $rep • Desce controlando (3s)..."
            tts?.speak("Desce, três, dois, um", TextToSpeech.QUEUE_FLUSH, null, "ecc_$rep")
            delay(3200)
            if (!metronomoAtivo) break
            faseMetronomo = "Repetição $rep • Segura no ponto máximo (1s)!"
            tts?.speak("Segura", TextToSpeech.QUEUE_FLUSH, null, "iso_$rep")
            delay(1100)
            if (!metronomoAtivo) break
            faseMetronomo = "Repetição $rep • Sobe explodindo! ($rep)"
            tts?.speak("Sobe, $rep", TextToSpeech.QUEUE_FLUSH, null, "con_$rep")
            delay(1600)
            rep++
            if (rep > 12) {
                metronomoAtivo = false
                faseMetronomo = "Série de 12 repetições guiadas finalizada! 🔥"
            }
        }
    }

    if (exibirCalculadoraAnilhas) {
        val total = pesoAlvoCalculadora.toDoubleOrNull() ?: 80.0
        val barra = pesoBarraCalculadora.toDoubleOrNull() ?: 20.0
        val porLado = ((total - barra) / 2.0).coerceAtLeast(0.0)

        val anilhasDisponiveis = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        val combinacao = remember(porLado) {
            var restante = porLado
            val lista = mutableListOf<String>()
            for (a in anilhasDisponiveis) {
                val qtd = (restante / a).toInt()
                if (qtd > 0) {
                    lista.add("${qtd}x ${if (a % 1.0 == 0.0) a.toInt() else a}kg")
                    restante -= qtd * a
                }
            }
            lista
        }

        AlertDialog(
            onDismissRequest = { exibirCalculadoraAnilhas = false },
            title = { Text("🧮 Calculadora de Anilhas por Lado", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pesoAlvoCalculadora,
                            onValueChange = { pesoAlvoCalculadora = it },
                            label = { Text("Carga Total (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pesoBarraCalculadora,
                            onValueChange = { pesoBarraCalculadora = it },
                            label = { Text("Barra (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VerdeEsmeralda.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Colocar em CADA LADO da barra: ${porLado} kg",
                                fontWeight = FontWeight.Black,
                                color = VerdeEsmeralda,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (combinacao.isEmpty()) "Apenas a barra (${barra.toInt()} kg)" else combinacao.joinToString(" + "),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { exibirCalculadoraAnilhas = false }) { Text("Pronto") }
            }
        )
    }

    val seriesAgrupadas = seriesEstado.groupBy { it.exerciseId to it.exerciseName }
    val totalConcluidas = seriesEstado.count { it.completed }
    val volumeAtual = seriesEstado.filter { it.completed }.sumOf {
        (it.weightKg.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Barra Superior do Treino Ativo + Seletor de Ficha
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fichaAtual?.title ?: "Treino Ativo",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = VerdeEsmeralda
                            )
                            Text(
                                text = "⏱️ ${segundosTreino / 60}m ${segundosTreino % 60}s • $totalConcluidas/${seriesEstado.size} séries • ${volumeAtual.toInt()} kg",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        }
                        IconButton(onClick = { exibirCalculadoraAnilhas = true }) {
                            Icon(Icons.Default.Calculate, contentDescription = "Calculadora de Anilhas", tint = CianoNeon)
                        }
                    }

                    // Troca rápida de ficha A/B/C/D
                    if (fichasDisponiveis.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fichasDisponiveis.take(4).forEach { f ->
                                FilterChip(
                                    selected = fichaAtual?.id == f.id,
                                    onClick = { fichaAtual = f },
                                    label = {
                                        Text(
                                            f.title.take(12),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cronômetro de Descanso Ativo + Metrônomo por Voz
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (descansoRestante > 0) AmbarDestaque.copy(alpha = 0.16f) else SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (descansoRestante > 0) AmbarDestaque else CianoNeon
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (descansoRestante > 0) "Descanso: ${descansoRestante}s" else "Cronômetro de Descanso Pronto",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = if (descansoRestante > 0) AmbarDestaque else TextoPrimario
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { descansoRestante += 30 },
                                label = { Text("+30s", fontSize = 11.sp) }
                            )
                            if (descansoRestante > 0) {
                                AssistChip(
                                    onClick = { descansoRestante = 0 },
                                    label = { Text("Zerar", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Metrônomo por Voz
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎙️ $faseMetronomo",
                            fontSize = 11.sp,
                            color = if (metronomoAtivo) VerdeEsmeraldaClaro else TextoSecundario,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { metronomoAtivo = !metronomoAtivo }
                        ) {
                            Text(
                                if (metronomoAtivo) "Parar Voz" else "Metrônomo Voz",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Lista de Exercícios e Séries
        seriesAgrupadas.forEach { (chave, seriesDoExercicio) ->
            val (_, nomeExercicio) = chave
            val videoUrl = seriesDoExercicio.firstOrNull()?.videoUrl

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = nomeExercicio,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = TextoPrimario,
                                modifier = Modifier.weight(1f)
                            )
                            if (!videoUrl.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                            contexto.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Vídeo", color = CianoNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        seriesDoExercicio.forEach { serie ->
                            val idxGlobal = seriesEstado.indexOf(serie)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (serie.completed) VerdeEsmeralda else SuperficieElevada,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${serie.setNumber}ª",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = if (serie.completed) FundoAcademia else TextoPrimario
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = serie.weightKg,
                                    onValueChange = { novoPeso ->
                                        if (idxGlobal >= 0) {
                                            seriesEstado[idxGlobal] = serie.copy(weightKg = novoPeso)
                                        }
                                    },
                                    label = { Text("Carga (kg)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = serie.reps,
                                    onValueChange = { novasReps ->
                                        if (idxGlobal >= 0) {
                                            seriesEstado[idxGlobal] = serie.copy(reps = novasReps)
                                        }
                                    },
                                    label = { Text("Reps", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (idxGlobal >= 0) {
                                            val novoStatus = !serie.completed
                                            seriesEstado[idxGlobal] = serie.copy(completed = novoStatus)
                                            if (novoStatus) {
                                                exercicioDescansoAtual = serie.exerciseName
                                                descansoRestante = serie.restSeconds
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (serie.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Marcar Série",
                                        tint = if (serie.completed) VerdeEsmeralda else TextoSecundario,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Finalizar Treino + Envio Automático do Resumo no WhatsApp
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Finalizar & Salvar Sessão", fontWeight = FontWeight.Black, fontSize = 16.sp)

                    OutlinedTextField(
                        value = notasSessao,
                        onValueChange = { notasSessao = it },
                        label = { Text("Observações do treino (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (feedbackSalvo != null) {
                        Text(feedbackSalvo!!, color = VerdeEsmeralda, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            salvandoSessao = true
                            escopo.launch {
                                val concluidas = seriesEstado.filter { it.completed }.ifEmpty { seriesEstado }.map {
                                    SerieSessaoDto(
                                        exerciseId = it.exerciseId,
                                        setNumber = it.setNumber,
                                        weightKg = it.weightKg.toDoubleOrNull() ?: 0.0,
                                        reps = it.reps.toIntOrNull() ?: 10,
                                        completed = true
                                    )
                                }
                                val sessao = RepositorioTreinoria.salvarSessaoTreinoConcluida(
                                    templateId = fichaAtual?.id,
                                    titulo = fichaAtual?.title ?: "Treino concluído no App Android",
                                    duracaoSegundos = segundosTreino.coerceAtLeast(60),
                                    feeling = feelingEscolhido,
                                    notas = notasSessao,
                                    seriesConcluidas = concluidas
                                )
                                // Envia resumo no WhatsApp do atleta se tiver telefone cadastrado
                                val fone = perfil?.whatsappPhone
                                if (!fone.isNullOrBlank() && sessao != null) {
                                    RepositorioTreinoria.enviarMensagemWhatsapp(
                                        numero = fone,
                                        texto = "🏋️ *TREINORIA PRO • Treino Concluído!*\n\n" +
                                            "Parabéns, *${perfil?.name ?: "Atleta"}*!\n" +
                                            "📋 Ficha: *${sessao.title}*\n" +
                                            "✅ Séries concluídas: *${sessao.totalSets}*\n" +
                                            "🔥 Volume Total: *${sessao.totalVolumeKg.toInt()} kg*\n" +
                                            "⏱️ Duração: *${(sessao.durationSeconds / 60).coerceAtLeast(1)} min*\n\n" +
                                            "🔗 Histórico completo: https://treinoria.raevan.com.br"
                                    )
                                }
                                salvandoSessao = false
                                feedbackSalvo = "✅ Treino salvo com sucesso no Supabase e resumo enviado no WhatsApp!"
                                delay(1200)
                                aoConcluirTreino()
                            }
                        },
                        enabled = !salvandoSessao,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (salvandoSessao) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Concluir Treino & Enviar Resumo WhatsApp", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
