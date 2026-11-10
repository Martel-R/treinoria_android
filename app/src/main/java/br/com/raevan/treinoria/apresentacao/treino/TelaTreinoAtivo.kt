package br.com.raevan.treinoria.apresentacao.treino

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.SerieSessaoDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.componentes.PlayerVideoNativoCard
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class CadencePresetAndroid(
    val id: String,
    val label: String,
    val eccentric: Int,
    val pause: Int,
    val concentric: Int
)

val CADENCE_PRESETS_ANDROID = listOf(
    CadencePresetAndroid("3-1-1", "Hipertrofia Clássica (3-1-1)", 3, 1, 1),
    CadencePresetAndroid("4-1-1", "Tempo Sob Tensão Máximo (4-1-1)", 4, 1, 1),
    CadencePresetAndroid("2-0-2", "Ritmo Constante & Fluido (2-0-2)", 2, 0, 2)
)

data class SerieEditavel(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val videoUrl: String?,
    val instructions: String?,
    val setNumber: Int,
    val totalSetsForExercise: Int,
    var weightKg: String,
    var reps: String,
    var completed: Boolean,
    val restSeconds: Int
)

enum class FaseExecucaoTelaInteira {
    EXECUTANDO_REPETICOES,
    EM_DESCANSO,
    PRONTO_PROXIMA_SERIE
}

private fun formatarPeso(valor: Double): String {
    return if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()
}

@Composable
fun TelaTreinoAtivo(
    fichaSelecionada: FichaTreinoDto?,
    aoConcluirTreino: () -> Unit
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var fichaAtual by remember { mutableStateOf(fichaSelecionada) }
    var perfil by remember { mutableStateOf<PerfilDto?>(null) }

    var segundosTreino by remember { mutableStateOf(0) }
    var treinoEmAndamento by remember { mutableStateOf(true) }

    // Confirmação de Descartar Treino
    var exibirConfirmacaoDescartar by remember { mutableStateOf(false) }

    // Seletor Rápido por Toque (Carga ou Repetições)
    var indiceSerieEditandoPeso by remember { mutableStateOf<Int?>(null) }
    var indiceSerieEditandoReps by remember { mutableStateOf<Int?>(null) }
    var aplicarEmTodasAsSeriesDoExercicio by remember { mutableStateOf(true) }
    var valorManualModal by remember { mutableStateOf("") }

    // Qual exercício está com o Player de Vídeo embutido aberto dentro do card
    var exercicioComVideoAbertoId by remember { mutableStateOf<String?>(null) }

    // Configuração de Cadência / Metrônomo
    var presetCadencia by remember { mutableStateOf(CADENCE_PRESETS_ANDROID.first()) }
    var vozAtiva by remember { mutableStateOf(true) }

    // Estado da Tela Inteira Minimalista de Contagem de Repetições -> Descanso -> Próxima Série
    var indiceSerieTelaInteira by remember { mutableStateOf<Int?>(null) }
    var faseTelaInteira by remember { mutableStateOf(FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) }
    var repAtualTelaInteira by remember { mutableStateOf(1) }
    var alvoRepsTelaInteira by remember { mutableStateOf(10) }
    var labelFaseMovimento by remember { mutableStateOf("PREPARE-SE") }
    var descansoTotalTelaInteira by remember { mutableStateOf(90) }
    var descansoRestanteTelaInteira by remember { mutableStateOf(0) }

    // Cronômetro de descanso da barra rápida
    var descansoBarraRapida by remember { mutableStateOf(0) }
    var exercicioDescansoBarra by remember { mutableStateOf("") }

    var salvandoSessao by remember { mutableStateOf(false) }
    var feelingEscolhido by remember { mutableStateOf("Ótimo 🔥") }
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

    val falar = { texto: String ->
        if (vozAtiva) {
            try {
                tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
            } catch (_: Throwable) {}
        }
    }

    // Modal Seletor Rápido de CARGA (kg) com 1 Toque
    if (indiceSerieEditandoPeso != null) {
        val idx = indiceSerieEditandoPeso!!
        val serieAlvo = seriesEstado.getOrNull(idx)
        if (serieAlvo != null) {
            val pesosRapidos = listOf(
                5.0, 10.0, 12.5, 15.0, 17.5, 20.0, 25.0, 30.0,
                35.0, 40.0, 45.0, 50.0, 60.0, 70.0, 80.0, 100.0
            )
            val aplicarPeso = { novoPesoStr: String ->
                if (aplicarEmTodasAsSeriesDoExercicio) {
                    seriesEstado.indices.forEach { i ->
                        if (seriesEstado[i].exerciseId == serieAlvo.exerciseId && !seriesEstado[i].completed) {
                            seriesEstado[i] = seriesEstado[i].copy(weightKg = novoPesoStr)
                        }
                    }
                }
                seriesEstado[idx] = seriesEstado[idx].copy(weightKg = novoPesoStr)
                indiceSerieEditandoPeso = null
            }

            AlertDialog(
                onDismissRequest = { indiceSerieEditandoPeso = null },
                title = {
                    Column {
                        Text("⚡ Selecionar Carga (kg)", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(
                            "${serieAlvo.exerciseName} • ${serieAlvo.setNumber}ª Série",
                            fontSize = 12.sp,
                            color = CianoNeon
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Ajuste fino rápido (-5, -2.5, +2.5, +5)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-5.0, -2.5, +2.5, +5.0).forEach { delta ->
                                FilledTonalButton(
                                    onClick = {
                                        val atual = serieAlvo.weightKg.toDoubleOrNull() ?: 20.0
                                        val novo = (atual + delta).coerceAtLeast(0.0)
                                        aplicarPeso(formatarPeso(novo))
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+${formatarPeso(delta)}" else formatarPeso(delta),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = if (delta > 0) VerdeEsmeralda else AmbarDestaque
                                    )
                                }
                            }
                        }

                        // Grade de Cargas Prontas com 1 Toque
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(190.dp)
                        ) {
                            items(pesosRapidos) { p ->
                                val pStr = formatarPeso(p)
                                val selecionado = serieAlvo.weightKg == pStr
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selecionado) VerdeEsmeralda else SuperficieElevada,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { aplicarPeso(pStr) }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "${pStr}kg",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = if (selecionado) FundoAcademia else TextoPrimario
                                        )
                                    }
                                }
                            }
                        }

                        // Opção de aplicar em todas as séries restantes deste exercício
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { aplicarEmTodasAsSeriesDoExercicio = !aplicarEmTodasAsSeriesDoExercicio }
                        ) {
                            Checkbox(
                                checked = aplicarEmTodasAsSeriesDoExercicio,
                                onCheckedChange = { aplicarEmTodasAsSeriesDoExercicio = it }
                            )
                            Text(
                                "Aplicar em todas as séries deste exercício",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Campo livre caso queira um peso específico (ex: 37.5)
                        OutlinedTextField(
                            value = valorManualModal,
                            onValueChange = { valorManualModal = it },
                            label = { Text("Ou digite outro peso (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    if (valorManualModal.isNotBlank()) {
                        Button(onClick = { aplicarPeso(valorManualModal.trim()) }) {
                            Text("Usar ${valorManualModal}kg", fontWeight = FontWeight.Black)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { indiceSerieEditandoPeso = null }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }

    // Modal Seletor Rápido de REPETIÇÕES com 1 Toque
    if (indiceSerieEditandoReps != null) {
        val idx = indiceSerieEditandoReps!!
        val serieAlvo = seriesEstado.getOrNull(idx)
        if (serieAlvo != null) {
            val repsRapidas = listOf(4, 6, 8, 10, 12, 15, 18, 20, 25, 30)
            val aplicarReps = { novasRepsStr: String ->
                if (aplicarEmTodasAsSeriesDoExercicio) {
                    seriesEstado.indices.forEach { i ->
                        if (seriesEstado[i].exerciseId == serieAlvo.exerciseId && !seriesEstado[i].completed) {
                            seriesEstado[i] = seriesEstado[i].copy(reps = novasRepsStr)
                        }
                    }
                }
                seriesEstado[idx] = seriesEstado[idx].copy(reps = novasRepsStr)
                indiceSerieEditandoReps = null
            }

            AlertDialog(
                onDismissRequest = { indiceSerieEditandoReps = null },
                title = {
                    Column {
                        Text("⚡ Selecionar Repetições", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(
                            "${serieAlvo.exerciseName} • ${serieAlvo.setNumber}ª Série",
                            fontSize = 12.sp,
                            color = CianoNeon
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(110.dp)
                        ) {
                            items(repsRapidas) { r ->
                                val rStr = r.toString()
                                val selecionado = serieAlvo.reps == rStr
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selecionado) CianoNeon else SuperficieElevada,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { aplicarReps(rStr) }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = rStr,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = if (selecionado) FundoAcademia else TextoPrimario
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { aplicarEmTodasAsSeriesDoExercicio = !aplicarEmTodasAsSeriesDoExercicio }
                        ) {
                            Checkbox(
                                checked = aplicarEmTodasAsSeriesDoExercicio,
                                onCheckedChange = { aplicarEmTodasAsSeriesDoExercicio = it }
                            )
                            Text(
                                "Aplicar em todas as séries deste exercício",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { indiceSerieEditandoReps = null }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }

    // Modal de confirmação para Descartar Treino em andamento
    if (exibirConfirmacaoDescartar) {
        AlertDialog(
            onDismissRequest = { exibirConfirmacaoDescartar = false },
            title = { Text("Descartar Treino?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Deseja realmente descartar este treino em andamento? As séries marcadas agora serão zeradas e não serão salvas no histórico.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        exibirConfirmacaoDescartar = false
                        indiceSerieTelaInteira = null
                        descansoBarraRapida = 0
                        segundosTreino = 0
                        seriesEstado.indices.forEach { i ->
                            seriesEstado[i] = seriesEstado[i].copy(completed = false)
                        }
                        aoConcluirTreino()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VermelhoAlerta)
                ) {
                    Text("Sim, Descartar Treino", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { exibirConfirmacaoDescartar = false }) {
                    Text("Continuar Treinando")
                }
            }
        )
    }

    // Carrega automaticamente APENAS o Treino do Dia
    LaunchedEffect(Unit) {
        RepositorioTreinoria.garantirDadosIniciaisUsuario()
        perfil = RepositorioTreinoria.carregarPerfil()
        val lista = RepositorioTreinoria.listarFichasTreino()
        val sessoes = RepositorioTreinoria.listarSessoesRecentes(10)
        if (fichaAtual == null && lista.isNotEmpty()) {
            fichaAtual = RepositorioTreinoria.selecionarTreinoDoDia(lista, sessoes)
        } else if (fichaAtual != null) {
            val atualizada = lista.find { it.id == fichaAtual?.id }
            if (atualizada != null) fichaAtual = atualizada
        }
    }

    // Popula séries quando o treino do dia é carregado
    LaunchedEffect(fichaAtual?.id, fichaAtual?.items?.size) {
        val f = fichaAtual ?: return@LaunchedEffect
        seriesEstado.clear()
        f.items.forEach { item ->
            val nomeEx = item.exercise?.name ?: "Exercício"
            val grupoMusc = item.exercise?.muscleGroup ?: "Musculação"
            val vid = item.exercise?.videoUrl
            val instr = item.exercise?.instructions ?: item.notes
            val qtdSeries = item.targetSets.coerceIn(1, 10)
            val repsPadrao = item.targetReps.split("-").lastOrNull()?.filter { it.isDigit() }?.ifBlank { "10" } ?: "10"
            val pesoPadrao = if ((item.targetWeightKg ?: 0.0) > 0) formatarPeso(item.targetWeightKg!!) else "20"
            for (s in 1..qtdSeries) {
                seriesEstado.add(
                    SerieEditavel(
                        exerciseId = item.exerciseId,
                        exerciseName = nomeEx,
                        muscleGroup = grupoMusc,
                        videoUrl = vid,
                        instructions = instr,
                        setNumber = s,
                        totalSetsForExercise = qtdSeries,
                        weightKg = pesoPadrao,
                        reps = repsPadrao,
                        completed = false,
                        restSeconds = item.restSeconds.takeIf { it > 0 } ?: 90
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

    // Cronômetro regressivo de descanso rápido
    LaunchedEffect(descansoBarraRapida) {
        if (descansoBarraRapida > 0) {
            delay(1000)
            descansoBarraRapida--
            if (descansoBarraRapida == 0) {
                GerenciadorNotificacoes.notificarFimDescanso(contexto, exercicioDescansoBarra)
                falar("Fim do descanso! Pronto para a próxima série.")
            }
        }
    }

    // Inicia uma série em TELA INTEIRA
    val iniciarSerieEmTelaInteira = { idxSerie: Int ->
        val serie = seriesEstado.getOrNull(idxSerie)
        if (serie != null) {
            val limiteReps = (serie.reps.toIntOrNull() ?: 10).coerceIn(1, 50)
            alvoRepsTelaInteira = limiteReps
            repAtualTelaInteira = 1
            descansoTotalTelaInteira = serie.restSeconds.coerceAtLeast(15)
            descansoRestanteTelaInteira = 0
            faseTelaInteira = FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES
            indiceSerieTelaInteira = idxSerie
        }
    }

    // Motor em Tempo Real da Tela Inteira Minimalista
    LaunchedEffect(indiceSerieTelaInteira, faseTelaInteira) {
        val idx = indiceSerieTelaInteira ?: return@LaunchedEffect
        val serieAtual = seriesEstado.getOrNull(idx) ?: return@LaunchedEffect

        if (faseTelaInteira == FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) {
            labelFaseMovimento = "PREPARAR..."
            falar("Preparar, ${alvoRepsTelaInteira} repetições")
            delay(1500)

            for (rep in 1..alvoRepsTelaInteira) {
                if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                repAtualTelaInteira = rep

                falar("Desce")
                for (seg in 1..presetCadencia.eccentric) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "DESCE (${seg}s)"
                    delay(1000)
                }

                if (presetCadencia.pause > 0) {
                    falar("Segura")
                    for (seg in 1..presetCadencia.pause) {
                        if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                        labelFaseMovimento = "SEGURA"
                        delay(1000)
                    }
                }

                falar("Sobe $rep")
                for (seg in 1..presetCadencia.concentric.coerceAtLeast(1)) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "SOBE!"
                    delay(1000)
                }
            }

            if (idx in seriesEstado.indices) {
                seriesEstado[idx] = seriesEstado[idx].copy(completed = true)
            }
            GerenciadorNotificacoes.vibrarFimDescanso(contexto)
            falar("Série concluída! Descanso iniciado.")
            descansoTotalTelaInteira = serieAtual.restSeconds.coerceAtLeast(15)
            descansoRestanteTelaInteira = descansoTotalTelaInteira
            faseTelaInteira = FaseExecucaoTelaInteira.EM_DESCANSO
        } else if (faseTelaInteira == FaseExecucaoTelaInteira.EM_DESCANSO) {
            while (descansoRestanteTelaInteira > 0 && indiceSerieTelaInteira == idx && faseTelaInteira == FaseExecucaoTelaInteira.EM_DESCANSO) {
                delay(1000)
                descansoRestanteTelaInteira--
            }
            if (indiceSerieTelaInteira == idx && faseTelaInteira == FaseExecucaoTelaInteira.EM_DESCANSO) {
                GerenciadorNotificacoes.notificarFimDescanso(contexto, serieAtual.exerciseName)
                falar("Descanso concluído! Toque para iniciar a próxima série.")
                faseTelaInteira = FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE
            }
        }
    }

    // TELA INTEIRA MINIMALISTA E LIMPA (Apenas o essencial!)
    if (indiceSerieTelaInteira != null) {
        val idxAtual = indiceSerieTelaInteira!!
        val serieAtual = seriesEstado.getOrNull(idxAtual)
        val proximaSerieIdx = seriesEstado.indices.firstOrNull { it > idxAtual && !seriesEstado[it].completed }
            ?: seriesEstado.indices.firstOrNull { !seriesEstado[it].completed && it != idxAtual }
        val proximaSerie = proximaSerieIdx?.let { seriesEstado.getOrNull(it) }

        if (serieAtual != null) {
            Dialog(
                onDismissRequest = { indiceSerieTelaInteira = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FundoAcademia
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = serieAtual.exerciseName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextoPrimario
                                )
                                Text(
                                    text = "${serieAtual.setNumber}ª Série • ${serieAtual.weightKg} kg",
                                    fontSize = 14.sp,
                                    color = CianoNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { indiceSerieTelaInteira = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = TextoSecundario
                                )
                            }
                        }

                        when (faseTelaInteira) {
                            FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(260.dp)
                                            .clip(CircleShape)
                                            .background(VerdeEsmeralda.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$repAtualTelaInteira",
                                                fontSize = 96.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VerdeEsmeralda
                                            )
                                            Text(
                                                text = "de $alvoRepsTelaInteira reps",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextoSecundario
                                            )
                                        }
                                    }

                                    Text(
                                        text = labelFaseMovimento,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextoPrimario,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            FaseExecucaoTelaInteira.EM_DESCANSO,
                            FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(250.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (descansoRestanteTelaInteira > 0)
                                                    AmbarDestaque.copy(alpha = 0.15f)
                                                else
                                                    VerdeEsmeralda.copy(alpha = 0.18f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = if (descansoRestanteTelaInteira > 0) "DESCANSO" else "PRONTO!",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (descansoRestanteTelaInteira > 0) AmbarDestaque else VerdeEsmeralda
                                            )
                                            Text(
                                                text = "${descansoRestanteTelaInteira}s",
                                                fontSize = 84.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (descansoRestanteTelaInteira > 0) AmbarDestaque else VerdeEsmeralda
                                            )
                                        }
                                    }

                                    if (proximaSerie != null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "PRÓXIMA SÉRIE",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = CianoNeon
                                            )
                                            Text(
                                                text = "${proximaSerie.exerciseName} • ${proximaSerie.setNumber}ª Série",
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Black,
                                                color = TextoPrimario,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "${proximaSerie.weightKg} kg × ${proximaSerie.reps} repetições",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VerdeEsmeraldaClaro
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Todas as séries desta ficha foram concluídas! 🏆",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VerdeEsmeralda,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            when (faseTelaInteira) {
                                FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES -> {
                                    Button(
                                        onClick = {
                                            if (idxAtual in seriesEstado.indices) {
                                                seriesEstado[idxAtual] = seriesEstado[idxAtual].copy(completed = true)
                                            }
                                            descansoTotalTelaInteira = serieAtual.restSeconds.coerceAtLeast(15)
                                            descansoRestanteTelaInteira = descansoTotalTelaInteira
                                            faseTelaInteira = FaseExecucaoTelaInteira.EM_DESCANSO
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                        modifier = Modifier.fillMaxWidth().height(60.dp)
                                    ) {
                                        Text(
                                            "Concluir Série & Iniciar Descanso",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                }

                                FaseExecucaoTelaInteira.EM_DESCANSO,
                                FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE -> {
                                    if (proximaSerie != null && proximaSerieIdx != null) {
                                        Button(
                                            onClick = {
                                                iniciarSerieEmTelaInteira(proximaSerieIdx)
                                            },
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(62.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Iniciar Próxima (${proximaSerie.setNumber}ª Série • ${proximaSerie.reps} reps)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { indiceSerieTelaInteira = null },
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(60.dp)
                                        ) {
                                            Text("Finalizar e Voltar ao Treino", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val seriesAgrupadas = seriesEstado.groupBy { it.exerciseId to it.exerciseName }
    val totalConcluidas = seriesEstado.count { it.completed }
    val volumeAtual = seriesEstado.filter { it.completed }.sumOf {
        (it.weightKg.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0)
    }
    val nomeDiaHoje = remember { RepositorioTreinoria.obterNomeDiaHoje() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Cabeçalho Limpo: Mostra APENAS o Treino do Dia + Botão Descartar
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TREINO DO DIA • ${nomeDiaHoje.uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = CianoNeon
                            )
                            Text(
                                text = fichaAtual?.name ?: "Carregando Treino do Dia...",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vozAtiva = !vozAtiva }) {
                                Icon(
                                    imageVector = if (vozAtiva) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Voz",
                                    tint = if (vozAtiva) VerdeEsmeralda else TextoSecundario
                                )
                            }
                            TextButton(
                                onClick = { exibirConfirmacaoDescartar = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = VermelhoAlerta)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Descartar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Seletor enxuto de Cadência para a Contagem
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cadência:", fontSize = 11.sp, color = TextoSecundario, fontWeight = FontWeight.Bold)
                        CADENCE_PRESETS_ANDROID.forEach { preset ->
                            FilterChip(
                                selected = presetCadencia.id == preset.id,
                                onClick = { presetCadencia = preset },
                                label = {
                                    Text(
                                        preset.id,
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

        // Lista de Exercícios e Séries do Treino do Dia (com Controles Rápidos +/- e Seletor 1-Toque)
        seriesAgrupadas.forEach { (chave, seriesDoExercicio) ->
            val (exId, nomeExercicio) = chave
            val primeiro = seriesDoExercicio.firstOrNull()
            val videoUrl = primeiro?.videoUrl
            val grupoMuscular = primeiro?.muscleGroup ?: ""
            val instrucoes = primeiro?.instructions
            val videoAbertoNesteCard = exercicioComVideoAbertoId == exId

            item(key = exId) {
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
                                if (grupoMuscular.isNotBlank()) {
                                    Text(
                                        text = grupoMuscular.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = VerdeEsmeralda
                                    )
                                }
                                Text(
                                    text = nomeExercicio,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = TextoPrimario
                                )
                            }
                            if (!videoUrl.isNullOrBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        exercicioComVideoAbertoId = if (videoAbertoNesteCard) null else exId
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (videoAbertoNesteCard) Icons.Default.Close else Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = CianoNeon,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (videoAbertoNesteCard) "Fechar Vídeo" else "Ver Vídeo",
                                        color = CianoNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Player de Vídeo Nativo Embutido no próprio Card (sem sair do app!)
                        if (videoAbertoNesteCard && !videoUrl.isNullOrBlank()) {
                            PlayerVideoNativoCard(
                                tituloExercicio = nomeExercicio,
                                grupoMuscular = grupoMuscular,
                                videoUrl = videoUrl,
                                instrucoes = instrucoes,
                                aoFechar = { exercicioComVideoAbertoId = null }
                            )
                        }

                        seriesDoExercicio.forEach { serie ->
                            val idxGlobal = seriesEstado.indexOf(serie)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (serie.completed) VerdeEsmeralda.copy(alpha = 0.12f) else SuperficieElevada,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Badge do Número da Série
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (serie.completed) VerdeEsmeralda else FundoAcademia,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${serie.setNumber}ª",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = if (serie.completed) FundoAcademia else TextoPrimario
                                            )
                                        }
                                    }

                                    // Controle Interativo de CARGA (kg): [-] [ 20 kg ] [+]
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = FundoAcademia,
                                        modifier = Modifier.weight(1.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SuperficieElevada)
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            val atual = serie.weightKg.toDoubleOrNull() ?: 20.0
                                                            val novo = (atual - 2.5).coerceAtLeast(0.0)
                                                            seriesEstado[idxGlobal] = serie.copy(weightKg = formatarPeso(novo))
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("−", fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextoSecundario)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            valorManualModal = ""
                                                            indiceSerieEditandoPeso = idxGlobal
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = "${serie.weightKg} kg",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.sp,
                                                    color = VerdeEsmeraldaClaro
                                                )
                                                Text(
                                                    text = "CARGA",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextoSecundario
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(VerdeEsmeralda.copy(alpha = 0.2f))
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            val atual = serie.weightKg.toDoubleOrNull() ?: 20.0
                                                            val novo = atual + 2.5
                                                            seriesEstado[idxGlobal] = serie.copy(weightKg = formatarPeso(novo))
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+", fontWeight = FontWeight.Black, fontSize = 16.sp, color = VerdeEsmeralda)
                                            }
                                        }
                                    }

                                    // Controle Interativo de REPETIÇÕES: [-] [ 10 reps ] [+]
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = FundoAcademia,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SuperficieElevada)
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            val atual = serie.reps.toIntOrNull() ?: 10
                                                            val novo = (atual - 1).coerceAtLeast(1)
                                                            seriesEstado[idxGlobal] = serie.copy(reps = novo.toString())
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("−", fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextoSecundario)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            indiceSerieEditandoReps = idxGlobal
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = serie.reps,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp,
                                                    color = CianoNeon
                                                )
                                                Text(
                                                    text = "REPS",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextoSecundario
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CianoNeon.copy(alpha = 0.2f))
                                                    .clickable {
                                                        if (idxGlobal >= 0) {
                                                            val atual = serie.reps.toIntOrNull() ?: 10
                                                            val novo = atual + 1
                                                            seriesEstado[idxGlobal] = serie.copy(reps = novo.toString())
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+", fontWeight = FontWeight.Black, fontSize = 16.sp, color = CianoNeon)
                                            }
                                        }
                                    }

                                    // Botão Iniciar Série em Tela Inteira
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(VerdeEsmeralda)
                                            .clickable {
                                                if (idxGlobal >= 0) {
                                                    iniciarSerieEmTelaInteira(idxGlobal)
                                                }
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Iniciar",
                                                tint = FundoAcademia,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Play",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = FundoAcademia
                                            )
                                        }
                                    }

                                    // Checkbox rápido de concluído
                                    IconButton(
                                        onClick = {
                                            if (idxGlobal >= 0) {
                                                val novoStatus = !serie.completed
                                                seriesEstado[idxGlobal] = serie.copy(completed = novoStatus)
                                                if (novoStatus) {
                                                    exercicioDescansoBarra = serie.exerciseName
                                                    descansoBarraRapida = serie.restSeconds
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (serie.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Concluir",
                                            tint = if (serie.completed) VerdeEsmeralda else TextoSecundario,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Finalizar ou Descartar Treino do Dia
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Finalizar ou Descartar Treino", fontWeight = FontWeight.Black, fontSize = 16.sp)

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
                                        isCompleted = true
                                    )
                                }
                                val sessao = RepositorioTreinoria.salvarSessaoTreinoConcluida(
                                    planId = fichaAtual?.id,
                                    titulo = fichaAtual?.name ?: "Treino concluído no App Android",
                                    duracaoSegundos = segundosTreino.coerceAtLeast(60),
                                    feeling = feelingEscolhido,
                                    notas = notasSessao,
                                    seriesConcluidas = concluidas
                                )
                                val fone = perfil?.whatsappPhone
                                if (!fone.isNullOrBlank() && sessao != null) {
                                    RepositorioTreinoria.enviarMensagemWhatsapp(
                                        numero = fone,
                                        texto = "🏋️ *TREINORIA PRO • Treino Concluído!*\n\n" +
                                            "Parabéns, *${perfil?.name ?: "Atleta"}*!\n" +
                                            "📋 Ficha: *${sessao.title}*\n" +
                                            "✅ Séries concluídas: *${concluidas.size}*\n" +
                                            "🔥 Volume Total: *${sessao.totalVolumeKg.toInt()} kg*\n" +
                                            "⏱️ Duração: *${sessao.durationMinutes ?: 45} min*\n\n" +
                                            "🔗 Histórico completo: https://treinoria.raevan.com.br"
                                    )
                                }
                                salvandoSessao = false
                                feedbackSalvo = "✅ Treino salvo com sucesso!"
                                delay(1000)
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
                            Text("Concluir & Salvar Treino", fontWeight = FontWeight.Black)
                        }
                    }

                    OutlinedButton(
                        onClick = { exibirConfirmacaoDescartar = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VermelhoAlerta),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descartar Treino em Andamento", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
