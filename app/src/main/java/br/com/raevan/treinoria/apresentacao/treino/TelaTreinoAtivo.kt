package br.com.raevan.treinoria.apresentacao.treino

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    // Carrega automaticamente APENAS o Treino do Dia (se nenhuma ficha específica foi clicada)
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
            val qtdSeries = item.targetSets.coerceIn(1, 10)
            val repsPadrao = item.targetReps.split("-").lastOrNull()?.filter { it.isDigit() }?.ifBlank { "10" } ?: "10"
            val pesoPadrao = if ((item.targetWeightKg ?: 0.0) > 0) item.targetWeightKg!!.toInt().toString() else "20"
            for (s in 1..qtdSeries) {
                seriesEstado.add(
                    SerieEditavel(
                        exerciseId = item.exerciseId,
                        exerciseName = nomeEx,
                        muscleGroup = grupoMusc,
                        videoUrl = vid,
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

                // Excêntrica (Descida)
                falar("Desce")
                for (seg in 1..presetCadencia.eccentric) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "DESCE (${seg}s)"
                    delay(1000)
                }

                // Pausa Isométrica
                if (presetCadencia.pause > 0) {
                    falar("Segura")
                    for (seg in 1..presetCadencia.pause) {
                        if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                        labelFaseMovimento = "SEGURA"
                        delay(1000)
                    }
                }

                // Concêntrica (Subida)
                falar("Sobe $rep")
                for (seg in 1..presetCadencia.concentric.coerceAtLeast(1)) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "SOBE!"
                    delay(1000)
                }
            }

            // Concluiu todas as repetições -> Marca série e inicia o Descanso na mesma tela
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
                        // Topo Minimalista: Apenas nome do exercício, série e botão discreto de fechar (X)
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

                        // Centro Focado e Gigante (Sem poluição visual)
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

                        // Rodapé Minimalista: APENAS 1 botão principal de ação!
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
        // Cabeçalho Limpo: Mostra APENAS o Treino do Dia (sem botões das 3 fichas)
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
                                fontSize = 19.sp,
                                color = VerdeEsmeralda
                            )
                            Text(
                                text = "⏱️ ${segundosTreino / 60}m ${segundosTreino % 60}s • $totalConcluidas/${seriesEstado.size} séries • ${volumeAtual.toInt()} kg",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        }
                        IconButton(onClick = { vozAtiva = !vozAtiva }) {
                            Icon(
                                imageVector = if (vozAtiva) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Voz",
                                tint = if (vozAtiva) VerdeEsmeralda else TextoSecundario
                            )
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

        // Lista de Exercícios e Séries do Treino do Dia
        seriesAgrupadas.forEach { (chave, seriesDoExercicio) ->
            val (_, nomeExercicio) = chave
            val videoUrl = seriesDoExercicio.firstOrNull()?.videoUrl
            val grupoMuscular = seriesDoExercicio.firstOrNull()?.muscleGroup ?: ""

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
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    label = { Text("kg", fontSize = 10.sp) },
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

                                // Botão Iniciar Repetição em Tela Inteira Minimalista!
                                FilledTonalButton(
                                    onClick = {
                                        if (idxGlobal >= 0) {
                                            iniciarSerieEmTelaInteira(idxGlobal)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Iniciar Repetições",
                                        tint = VerdeEsmeralda,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Iniciar", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }

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
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (serie.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Marcar Série",
                                        tint = if (serie.completed) VerdeEsmeralda else TextoSecundario,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Finalizar Treino do Dia
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Finalizar Treino do Dia", fontWeight = FontWeight.Black, fontSize = 16.sp)

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
                            Text("Concluir Treino do Dia", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
