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

    var fichasDisponiveis by remember { mutableStateOf<List<FichaTreinoDto>>(emptyList()) }
    var fichaAtual by remember { mutableStateOf(fichaSelecionada) }
    var perfil by remember { mutableStateOf<PerfilDto?>(null) }

    var segundosTreino by remember { mutableStateOf(0) }
    var treinoEmAndamento by remember { mutableStateOf(true) }

    // Configuração de Cadência / Metrônomo
    var presetCadencia by remember { mutableStateOf(CADENCE_PRESETS_ANDROID.first()) }
    var vozAtiva by remember { mutableStateOf(true) }

    // Estado da Tela Inteira de Contagem de Repetições + Descanso + Sugestão da Próxima Série
    var indiceSerieTelaInteira by remember { mutableStateOf<Int?>(null) }
    var faseTelaInteira by remember { mutableStateOf(FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) }
    var repAtualTelaInteira by remember { mutableStateOf(1) }
    var alvoRepsTelaInteira by remember { mutableStateOf(10) }
    var labelFaseMovimento by remember { mutableStateOf("PREPARE-SE...") }
    var subLabelMovimento by remember { mutableStateOf("Segure a carga na posição inicial") }
    var descansoTotalTelaInteira by remember { mutableStateOf(90) }
    var descansoRestanteTelaInteira by remember { mutableStateOf(0) }

    // Cronômetro de descanso da barra rápida (quando marca concluído manual sem abrir tela cheia)
    var descansoBarraRapida by remember { mutableStateOf(0) }
    var exercicioDescansoBarra by remember { mutableStateOf("") }

    var exibirCalculadoraAnilhas by remember { mutableStateOf(false) }
    var pesoAlvoCalculadora by remember { mutableStateOf("80") }
    var pesoBarraCalculadora by remember { mutableStateOf("20") }

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

    // Carrega fichas se nenhuma foi passada
    LaunchedEffect(Unit) {
        RepositorioTreinoria.garantirDadosIniciaisUsuario()
        perfil = RepositorioTreinoria.carregarPerfil()
        val lista = RepositorioTreinoria.listarFichasTreino()
        fichasDisponiveis = lista
        if (fichaAtual == null && lista.isNotEmpty()) {
            fichaAtual = lista.first()
        } else if (fichaAtual != null) {
            val atualizada = lista.find { it.id == fichaAtual?.id }
            if (atualizada != null) fichaAtual = atualizada
        }
    }

    // Popula séries quando a ficha muda
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

    // Cronômetro regressivo de descanso da barra rápida
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

    // Função para iniciar uma série em TELA INTEIRA a partir da configuração de cadência e repetições
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

    // Motor em Tempo Real da Tela Inteira:
    // 1) Conta cada repetição (Excêntrica -> Pausa -> Concêntrica) até `alvoRepsTelaInteira`
    // 2) Ao terminar a última repetição, marca a série como concluída e inicia o DESCANSO na mesma tela
    // 3) Ao acabar o descanso (ou se o usuário pular), transiciona para PRONTO_PROXIMA_SERIE e aguarda o clique do usuário!
    LaunchedEffect(indiceSerieTelaInteira, faseTelaInteira) {
        val idx = indiceSerieTelaInteira ?: return@LaunchedEffect
        val serieAtual = seriesEstado.getOrNull(idx) ?: return@LaunchedEffect

        if (faseTelaInteira == FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) {
            labelFaseMovimento = "PREPARAR..."
            subLabelMovimento = "${serieAtual.exerciseName} • ${serieAtual.setNumber}ª Série (${alvoRepsTelaInteira} reps)"
            falar("Preparar para ${serieAtual.exerciseName}, ${alvoRepsTelaInteira} repetições")
            delay(1800)

            for (rep in 1..alvoRepsTelaInteira) {
                if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                repAtualTelaInteira = rep

                // Fase Excêntrica (Descida controlada)
                falar("Desce")
                for (seg in 1..presetCadencia.eccentric) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "⬇️ DESCENDO ($seg/${presetCadencia.eccentric}s)"
                    subLabelMovimento = "Controle a fase excêntrica"
                    delay(1000)
                }

                // Fase Isométrica / Pausa na base
                if (presetCadencia.pause > 0) {
                    falar("Segura")
                    for (seg in 1..presetCadencia.pause) {
                        if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                        labelFaseMovimento = "⏸️ SEGURA EMBAIXO!"
                        subLabelMovimento = "Máxima tensão muscular"
                        delay(1000)
                    }
                }

                // Fase Concêntrica (Subida explosiva)
                falar("Sobe $rep")
                for (seg in 1..presetCadencia.concentric.coerceAtLeast(1)) {
                    if (indiceSerieTelaInteira != idx || faseTelaInteira != FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) return@LaunchedEffect
                    labelFaseMovimento = "⬆️ SOBE FORTE! ($rep/$alvoRepsTelaInteira)"
                    subLabelMovimento = "Contraia no topo do movimento"
                    delay(1000)
                }
            }

            // Terminou todas as repetições configuradas para esta série!
            // Marca a série como concluída e inicia imediatamente a contagem de descanso na MESMA tela inteira
            if (idx in seriesEstado.indices) {
                seriesEstado[idx] = seriesEstado[idx].copy(completed = true)
            }
            GerenciadorNotificacoes.vibrarFimDescanso(contexto)
            falar("Série concluída! Iniciando descanso de ${serieAtual.restSeconds} segundos.")
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
                falar("Descanso finalizado! Toque no botão quando estiver pronto para iniciar a próxima série.")
                faseTelaInteira = FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE
            }
        }
    }

    // MODAL DE TELA INTEIRA: Contagem de Repetições -> Descanso na Mesma Tela -> Sugestão da Próxima Série (aguardando clique)
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Topo da Tela Inteira
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = serieAtual.muscleGroup.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = VerdeEsmeralda
                                )
                                Text(
                                    text = serieAtual.exerciseName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextoPrimario
                                )
                                Text(
                                    text = "${serieAtual.setNumber}ª de ${serieAtual.totalSetsForExercise} Séries • ${serieAtual.weightKg} kg × ${alvoRepsTelaInteira} reps • Ritmo ${presetCadencia.id}",
                                    fontSize = 12.sp,
                                    color = CianoNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                IconButton(onClick = { vozAtiva = !vozAtiva }) {
                                    Icon(
                                        imageVector = if (vozAtiva) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "Voz",
                                        tint = if (vozAtiva) VerdeEsmeralda else TextoSecundario
                                    )
                                }
                                IconButton(onClick = { indiceSerieTelaInteira = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar Tela Inteira",
                                        tint = TextoSecundario
                                    )
                                }
                            }
                        }

                        // Centro Gigante de acordo com a Fase (Executando Repetições | Em Descanso | Pronto p/ Próxima Série)
                        when (faseTelaInteira) {
                            FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = RoxoDestaque.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "🎧 CADÊNCIA GUIADA ${presetCadencia.id}",
                                            color = RoxoDestaque,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Círculo Gigante com Contador de Repetições
                                    Box(
                                        modifier = Modifier
                                            .size(230.dp)
                                            .clip(CircleShape)
                                            .background(VerdeEsmeralda.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "REPETIÇÃO",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextoSecundario
                                            )
                                            Text(
                                                text = "$repAtualTelaInteira/$alvoRepsTelaInteira",
                                                fontSize = 68.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VerdeEsmeralda
                                            )
                                            Text(
                                                text = "${serieAtual.weightKg} kg",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CianoNeon
                                            )
                                        }
                                    }

                                    Text(
                                        text = labelFaseMovimento,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextoPrimario,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = subLabelMovimento,
                                        fontSize = 14.sp,
                                        color = TextoSecundario,
                                        textAlign = TextAlign.Center
                                    )

                                    LinearProgressIndicator(
                                        progress = {
                                            (repAtualTelaInteira.toFloat() / alvoRepsTelaInteira.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        color = VerdeEsmeralda
                                    )
                                }
                            }

                            FaseExecucaoTelaInteira.EM_DESCANSO -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = VerdeEsmeralda.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "✅ ${serieAtual.setNumber}ª SÉRIE CONCLUÍDA! RECUPERE O FÔLEGO",
                                            color = VerdeEsmeraldaClaro,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(220.dp)
                                            .clip(CircleShape)
                                            .background(AmbarDestaque.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "DESCANSO",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmbarDestaque
                                            )
                                            Text(
                                                text = "${descansoRestanteTelaInteira}s",
                                                fontSize = 68.sp,
                                                fontWeight = FontWeight.Black,
                                                color = AmbarDestaque
                                            )
                                            Text(
                                                text = "Meta: ${descansoTotalTelaInteira}s",
                                                fontSize = 13.sp,
                                                color = TextoSecundario
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = { descansoRestanteTelaInteira = (descansoRestanteTelaInteira - 15).coerceAtLeast(0) }
                                        ) {
                                            Text("-15s", fontWeight = FontWeight.Bold)
                                        }
                                        FilledTonalButton(
                                            onClick = { descansoRestanteTelaInteira += 30 }
                                        ) {
                                            Text("+30s", fontWeight = FontWeight.Bold)
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                descansoRestanteTelaInteira = 0
                                                faseTelaInteira = FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE
                                            }
                                        ) {
                                            Text("Pular Descanso", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Card de Prévia / Sugestão da Próxima Série já durante o descanso
                                    if (proximaSerie != null) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = SuperficieCard,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "⏭️ PRÓXIMA SÉRIE SUGERIDA:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = CianoNeon
                                                )
                                                Text(
                                                    text = "${proximaSerie.exerciseName} • ${proximaSerie.setNumber}ª Série",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = TextoPrimario
                                                )
                                                Text(
                                                    text = "Carga: ${proximaSerie.weightKg} kg × ${proximaSerie.reps} repetições",
                                                    fontSize = 13.sp,
                                                    color = VerdeEsmeraldaClaro,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            FaseExecucaoTelaInteira.PRONTO_PROXIMA_SERIE -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(CircleShape)
                                            .background(VerdeEsmeralda.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = VerdeEsmeralda,
                                            modifier = Modifier.size(92.dp)
                                        )
                                    }

                                    Text(
                                        text = "DESCANSO CONCLUÍDO! 🔥",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        color = VerdeEsmeralda,
                                        textAlign = TextAlign.Center
                                    )

                                    if (proximaSerie != null) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = SuperficieCard,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(18.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "PRONTO PARA A PRÓXIMA SÉRIE?",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = CianoNeon
                                                )
                                                Text(
                                                    text = proximaSerie.exerciseName,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = TextoPrimario,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "${proximaSerie.setNumber}ª Série • ${proximaSerie.weightKg} kg × ${proximaSerie.reps} repetições",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VerdeEsmeraldaClaro
                                                )
                                                Text(
                                                    text = "A contagem só iniciará quando você tocar no botão abaixo.",
                                                    fontSize = 12.sp,
                                                    color = TextoSecundario,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Parabéns! Você concluiu todas as séries planejadas desta ficha!",
                                            fontSize = 15.sp,
                                            color = TextoPrimario,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Rodapé da Tela Inteira com Botões de Ação
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                        modifier = Modifier.fillMaxWidth().height(56.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Concluir Série Agora & Iniciar Descanso",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp
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
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(58.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Iniciar Próxima: ${proximaSerie.setNumber}ª Série (${proximaSerie.reps} reps)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { indiceSerieTelaInteira = null },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Voltar para Visão Geral do Treino", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
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
                                "Colocar em CADA LADO da barra: $porLado kg",
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
        // Barra Superior do Treino Ativo + Seletor de Ficha A/B/C/D
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
                                text = fichaAtual?.name ?: "Treino Ativo",
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
                                            f.name.take(14),
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

        // Configuração do Ritmo / Cadência para a Tela Inteira de Repetições
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎧 Configuração de Cadência (Tela Inteira)",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = RoxoDestaque
                            )
                            Text(
                                text = "Toque em ▶ Iniciar Série abaixo para abrir a contagem em tela cheia",
                                fontSize = 11.sp,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CADENCE_PRESETS_ANDROID.forEach { preset ->
                            FilterChip(
                                selected = presetCadencia.id == preset.id,
                                onClick = { presetCadencia = preset },
                                label = {
                                    Text(
                                        "Ritmo ${preset.id}",
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

        // Lista de Exercícios e Séries da Ficha
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

                                // Botão Iniciar Repetição em Tela Inteira!
                                FilledTonalButton(
                                    onClick = {
                                        if (idxGlobal >= 0) {
                                            iniciarSerieEmTelaInteira(idxGlobal)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Iniciar Repetições em Tela Inteira",
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
                                feedbackSalvo = "✅ Treino salvo com sucesso e resumo enviado no WhatsApp!"
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
