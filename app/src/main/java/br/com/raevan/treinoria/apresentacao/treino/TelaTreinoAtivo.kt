package br.com.raevan.treinoria.apresentacao.treino

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.raevan.treinoria.dados.remoto.dto.ExercicioDto
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.SerieSessaoDto
import br.com.raevan.treinoria.dados.repositorios.*
import br.com.raevan.treinoria.nucleo.componentes.BarraControleMidiaTelaCheia
import br.com.raevan.treinoria.nucleo.componentes.DialogoCameraAoVivoCoachIa
import br.com.raevan.treinoria.nucleo.componentes.DialogoVerificacaoHumorPosTreino
import br.com.raevan.treinoria.nucleo.componentes.GeradorCardTreinoStories
import br.com.raevan.treinoria.nucleo.componentes.PlayerVideoNativoCard
import br.com.raevan.treinoria.nucleo.componentes.ResumoExercicioCard
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.saude.GerenciadorHealthConnect
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
    val equipment: String,
    val videoUrl: String?,
    val instructions: String?,
    val targetRepsRange: String,
    val setNumber: Int,
    var weightKg: String,
    var reps: String,
    var isWarmup: Boolean = false,
    var completed: Boolean = false,
    val restSeconds: Int = 90,
    val prevWeightKg: Double? = null,
    val prevReps: Int? = null,
    val maxPrWeightKg: Double? = null,
    val aiBadge: String? = null
)

enum class FaseExecucaoTelaInteira {
    EXECUTANDO_REPETICOES,
    EM_DESCANSO,
    PRONTO_PROXIMA_SERIE
}

private fun formatarPeso(valor: Double): String {
    return if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaTreinoAtivo(
    fichaSelecionada: FichaTreinoDto?,
    aoConcluirTreino: () -> Unit
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var fichaAtual by remember {
        mutableStateOf(fichaSelecionada ?: GerenciadorTreinoAtivoSessao.fichaAtivaObj)
    }
    var perfil by remember { mutableStateOf<PerfilDto?>(RepositorioTreinoria.cachePerfil) }
    var catalogoExercicios by remember { mutableStateOf<List<ExercicioDto>>(RepositorioTreinoria.cacheExercicios) }
    var estatisticasExercicios by remember {
        mutableStateOf<Map<String, EstatisticaExercicioHistorico>>(RepositorioTreinoria.cacheEstatisticasExercicios)
    }

    // Referências diretas ao Singleton GerenciadorTreinoAtivoSessao para NUNCA perder dados ao trocar de aba!
    val seriesEstado = GerenciadorTreinoAtivoSessao.seriesEstado
    val exerciciosExpandidosIds = GerenciadorTreinoAtivoSessao.exerciciosExpandidosIds
    val mensagensChatPreTreino = GerenciadorTreinoAtivoSessao.mensagensChatPreTreino

    var chipRefeicaoEscolhido by remember { mutableStateOf("") }
    var chipCardioEscolhido by remember { mutableStateOf("") }
    var chipEnergiaEscolhido by remember { mutableStateOf("") }
    var textoLivreCheckin by remember { mutableStateOf("") }
    var consultandoCheckinIa by remember { mutableStateOf(false) }

    // Modal de Conclusão do Treino
    var exibirModalHumorPosTreino by remember { mutableStateOf(false) }
    var exibirModalConcluirTreino by remember { mutableStateOf(false) }
    var feelingEscolhido by remember { mutableStateOf("Ótimo") }
    var notasSessao by remember { mutableStateOf("") }
    var fotoPosTreinoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var enviarResumoWhatsapp by remember { mutableStateOf(true) }
    var salvandoSessao by remember { mutableStateOf(false) }
    var feedbackSalvo by remember { mutableStateOf<String?>(null) }

    val cameraPosTreinoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) fotoPosTreinoBitmap = bmp
    }

    val galeriaPosTreinoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contexto.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contexto.contentResolver, uri)
                }
                fotoPosTreinoBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Throwable) {}
        }
    }

    var exibirConfirmacaoDescartar by remember { mutableStateOf(false) }
    var exercicioIdParaTrocarEquivalente by remember { mutableStateOf<String?>(null) }
    var exibirAdicionarExercicioModal by remember { mutableStateOf(false) }
    var buscaAdicionarExercicio by remember { mutableStateOf("") }

    var exibirCalculadoraAnilhas by remember { mutableStateOf(false) }
    var pesoAlvoCalculadora by remember { mutableStateOf("60") }
    var pesoBarraCalculadora by remember { mutableStateOf("20") }
    var nomeExercicioCalculadora by remember { mutableStateOf("") }

    var indiceSerieEditandoPeso by remember { mutableStateOf<Int?>(null) }
    var indiceSerieEditandoReps by remember { mutableStateOf<Int?>(null) }
    var aplicarEmTodasAsSeriesDoExercicio by remember { mutableStateOf(true) }
    var valorManualModal by remember { mutableStateOf("") }

    var exercicioComVideoAbertoId by remember { mutableStateOf<String?>(null) }

    var presetCadencia by remember { mutableStateOf(CADENCE_PRESETS_ANDROID.first()) }
    var vozAtiva by remember { mutableStateOf(true) }

    var indiceSerieCameraAoVivoIa by remember { mutableStateOf<Int?>(null) }

    var indiceSerieTelaInteira by remember { mutableStateOf<Int?>(null) }
    var faseTelaInteira by remember { mutableStateOf(FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES) }
    var repAtualTelaInteira by remember { mutableStateOf(1) }
    var alvoRepsTelaInteira by remember { mutableStateOf(10) }
    var labelFaseMovimento by remember { mutableStateOf("PREPARE-SE") }
    var descansoTotalTelaInteira by remember { mutableStateOf(90) }
    var descansoRestanteTelaInteira by remember { mutableStateOf(0) }

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

    val verificarAutoAvancoExercicio = { exIdConcluido: String ->
        val todasDesteConcluidas = seriesEstado
            .filter { it.exerciseId == exIdConcluido }
            .all { it.completed }
        if (todasDesteConcluidas && GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) {
            val proximoPendenteId = seriesEstado.firstOrNull { !it.completed }?.exerciseId
            exerciciosExpandidosIds.clear()
            if (proximoPendenteId != null) {
                exerciciosExpandidosIds.add(proximoPendenteId)
            }
        }
        if (seriesEstado.isNotEmpty() && seriesEstado.all { it.completed }) {
            exibirModalHumorPosTreino = true
        }
    }

    val alternarExpansaoExercicio = { exId: String ->
        val jaExpandido = exerciciosExpandidosIds.contains(exId)
        if (jaExpandido) {
            exerciciosExpandidosIds.remove(exId)
        } else {
            if (GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) {
                exerciciosExpandidosIds.clear()
            }
            exerciciosExpandidosIds.add(exId)
        }
    }

    LaunchedEffect(Unit) {
        RepositorioTreinoria.garantirDadosIniciaisUsuario()
        perfil = RepositorioTreinoria.carregarPerfil()
        catalogoExercicios = RepositorioTreinoria.listarExercicios()
        estatisticasExercicios = RepositorioTreinoria.obterEstatisticasExercicios()
        val lista = RepositorioTreinoria.listarFichasTreino()
        val sessoes = RepositorioTreinoria.listarSessoesRecentes(10)
        if (fichaAtual == null && lista.isNotEmpty()) {
            fichaAtual = GerenciadorTreinoAtivoSessao.fichaAtivaObj
                ?: RepositorioTreinoria.selecionarTreinoDoDia(lista, sessoes)
        } else if (fichaAtual != null) {
            val atualizada = lista.find { it.id == fichaAtual?.id }
            if (atualizada != null) fichaAtual = atualizada
        }
    }

    LaunchedEffect(fichaAtual?.name, perfil?.name) {
        val nomeTreino = fichaAtual?.name ?: return@LaunchedEffect
        if (!GerenciadorTreinoAtivoSessao.jaMostrouBoasVindasCheckin) {
            GerenciadorTreinoAtivoSessao.jaMostrouBoasVindasCheckin = true
            val primeiroNome = perfil?.name?.split(" ")?.firstOrNull() ?: "Atleta"
            mensagensChatPreTreino.clear()
            mensagensChatPreTreino.add(
                MensagemChatIa(
                    role = "assistant",
                    content = "Fala, $primeiroNome! 💪 Vi que hoje o seu treino é $nomeTreino e já carreguei todo o seu histórico de cargas, medidas, mapa de fadiga, hidratação e smartwatch.\n\n" +
                        "Antes de começarmos o 1º exercício, me conta rapidinho:\n" +
                        "1️⃣ O que você comeu no pré-treino (ou está em jejum)?\n" +
                        "2️⃣ Já fez ou vai fazer aeróbico/cardio hoje?\n" +
                        "3️⃣ Como está sua energia/sono ou quer trocar/incluir algum aparelho?\n\n" +
                        "Com base nisso eu já calibro suas cargas, séries e exercícios de hoje!"
                )
            )
        }
    }

    // Inicializa as séries na memória SOMENTE se ainda não houver treino ativo para esta ficha!
    LaunchedEffect(fichaAtual?.id, fichaAtual?.items?.size, estatisticasExercicios.size) {
        val f = fichaAtual ?: return@LaunchedEffect
        // Se já existe um treino em andamento para esta mesma ficha, preserva TODAS as séries e edições!
        if (seriesEstado.isNotEmpty() && GerenciadorTreinoAtivoSessao.fichaAtivaId == f.id) {
            return@LaunchedEffect
        }
        GerenciadorTreinoAtivoSessao.fichaAtivaId = f.id
        GerenciadorTreinoAtivoSessao.fichaAtivaObj = f
        seriesEstado.clear()
        f.items.forEach { item ->
            val nomeEx = item.exercise?.name ?: "Exercício"
            val grupoMusc = item.exercise?.muscleGroup ?: "Musculação"
            val equip = item.exercise?.equipment ?: "Máquina / Livre"
            val vid = item.exercise?.videoUrl
            val instr = item.exercise?.instructions ?: item.notes
            val hist = estatisticasExercicios[item.exerciseId]
            val qtdSeries = item.targetSets.coerceIn(1, 10)
            val repsPadrao = (hist?.lastReps?.toString())
                ?: item.targetReps.split("-").lastOrNull()?.filter { it.isDigit() }?.ifBlank { "10" }
                ?: "10"
            val pesoPadrao = when {
                (hist?.lastWeightKg ?: 0.0) > 0 -> formatarPeso(hist!!.lastWeightKg)
                (item.targetWeightKg ?: 0.0) > 0 -> formatarPeso(item.targetWeightKg!!)
                else -> "20"
            }
            for (s in 1..qtdSeries) {
                seriesEstado.add(
                    SerieEditavel(
                        exerciseId = item.exerciseId,
                        exerciseName = nomeEx,
                        muscleGroup = grupoMusc,
                        equipment = equip,
                        videoUrl = vid,
                        instructions = instr,
                        targetRepsRange = item.targetReps,
                        setNumber = s,
                        weightKg = pesoPadrao,
                        reps = repsPadrao,
                        isWarmup = false,
                        completed = false,
                        restSeconds = item.restSeconds.takeIf { it > 0 } ?: 90,
                        prevWeightKg = hist?.lastWeightKg,
                        prevReps = hist?.lastReps,
                        maxPrWeightKg = hist?.maxWeightKg
                    )
                )
            }
        }
        exerciciosExpandidosIds.clear()
        seriesEstado.firstOrNull()?.exerciseId?.let { exerciciosExpandidosIds.add(it) }
    }

    // Cronômetro persistente (mesmo trocando de aba, compensa o tempo decorrido!)
    LaunchedEffect(Unit) {
        val agora = System.currentTimeMillis()
        if (GerenciadorTreinoAtivoSessao.ultimoTickTimestampMs > 0L) {
            val deltaSeg = ((agora - GerenciadorTreinoAtivoSessao.ultimoTickTimestampMs) / 1000L).toInt().coerceIn(0, 7200)
            GerenciadorTreinoAtivoSessao.segundosTreino += deltaSeg
        }
        GerenciadorTreinoAtivoSessao.ultimoTickTimestampMs = agora
        while (true) {
            delay(1000)
            GerenciadorTreinoAtivoSessao.segundosTreino++
            GerenciadorTreinoAtivoSessao.ultimoTickTimestampMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(GerenciadorTreinoAtivoSessao.descansoBarraRapida) {
        if (GerenciadorTreinoAtivoSessao.descansoBarraRapida > 0) {
            delay(1000)
            GerenciadorTreinoAtivoSessao.descansoBarraRapida--
            if (GerenciadorTreinoAtivoSessao.descansoBarraRapida == 0) {
                GerenciadorNotificacoes.notificarFimDescanso(
                    contexto,
                    GerenciadorTreinoAtivoSessao.exercicioDescansoBarra
                )
                falar("Fim do descanso! Pronto para a próxima série.")
            }
        }
    }

    val seriesAgrupadas = seriesEstado.groupBy { it.exerciseId to it.exerciseName }
    val totalConcluidas = seriesEstado.count { it.completed }
    val volumeAtual = seriesEstado.filter { it.completed }.ifEmpty { seriesEstado }.sumOf {
        (it.weightKg.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0)
    }
    val nomeDiaHoje = remember { RepositorioTreinoria.obterNomeDiaHoje() }

    val prsBatidosNaSessao = remember(seriesEstado.toList(), estatisticasExercicios) {
        val listaPrs = mutableListOf<Triple<String, Double, Int>>()
        seriesEstado.groupBy { it.exerciseId }.forEach { (exId, lista) ->
            val prAntigo = estatisticasExercicios[exId]?.maxWeightKg ?: 0.0
            val melhorSerie = lista
                .filter { (it.completed || totalConcluidas == 0) && (it.weightKg.toDoubleOrNull() ?: 0.0) > prAntigo }
                .maxByOrNull { it.weightKg.toDoubleOrNull() ?: 0.0 }
            val pesoMelhor = melhorSerie?.weightKg?.toDoubleOrNull() ?: 0.0
            if (melhorSerie != null && pesoMelhor > 0 && prAntigo > 0) {
                listaPrs.add(
                    Triple(
                        melhorSerie.exerciseName,
                        pesoMelhor,
                        melhorSerie.reps.toIntOrNull() ?: 10
                    )
                )
            }
        }
        listaPrs
    }

    if (indiceSerieCameraAoVivoIa != null) {
        val idxCam = indiceSerieCameraAoVivoIa!!
        val serieCam = seriesEstado.getOrNull(idxCam)
        if (serieCam != null) {
            val totalSeriesDesteEx = seriesEstado.count { it.exerciseId == serieCam.exerciseId }.coerceAtLeast(1)
            val proxIdx = seriesEstado.indices.firstOrNull { it > idxCam && !seriesEstado[it].completed }
            val proxSerieObj = proxIdx?.let { seriesEstado.getOrNull(it) }

            DialogoCameraAoVivoCoachIa(
                nomeExercicio = serieCam.exerciseName,
                grupoMuscular = serieCam.muscleGroup,
                numeroSerie = serieCam.setNumber,
                totalSeriesDoExercicio = totalSeriesDesteEx,
                alvoRepsStr = serieCam.reps,
                pesoKgStr = serieCam.weightKg,
                descansoSegundos = serieCam.restSeconds,
                presetCadencia = presetCadencia,
                tts = tts,
                vozInicialAtiva = vozAtiva,
                aoConcluirSerie = { repsContadas ->
                    if (idxCam in seriesEstado.indices) {
                        seriesEstado[idxCam] = seriesEstado[idxCam].copy(
                            reps = repsContadas.toString(),
                            completed = true
                        )
                    }
                    verificarAutoAvancoExercicio(serieCam.exerciseId)
                    GerenciadorTreinoAtivoSessao.exercicioDescansoBarra = serieCam.exerciseName
                    GerenciadorTreinoAtivoSessao.descansoBarraRapida = serieCam.restSeconds
                },
                aoAvancarParaProximaSerie = if (proxIdx != null) {
                    {
                        val proxExId = seriesEstado.getOrNull(proxIdx)?.exerciseId
                        if (proxExId != null && GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) {
                            exerciciosExpandidosIds.clear()
                            exerciciosExpandidosIds.add(proxExId)
                        }
                        indiceSerieCameraAoVivoIa = proxIdx
                    }
                } else null,
                proximaSerieDescricao = proxSerieObj?.let { "${it.exerciseName} • ${it.setNumber}ª Série (${it.weightKg}kg × ${it.reps}r)" },
                aoFechar = { indiceSerieCameraAoVivoIa = null }
            )
        }
    }

    if (exibirModalHumorPosTreino) {
        val cal = java.util.Calendar.getInstance()
        val diaIdx = (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        DialogoVerificacaoHumorPosTreino(
            checkinsNaSemana = 1,
            diaSemanaAtualIdx = diaIdx,
            aoConcluirVerificacao = { feelingFormatado ->
                feelingEscolhido = feelingFormatado
                exibirModalHumorPosTreino = false
                exibirModalConcluirTreino = true
            },
            aoFechar = { exibirModalHumorPosTreino = false }
        )
    }

    // Modal de Conclusão do Treino
    if (exibirModalConcluirTreino) {
        val duracaoMin = (GerenciadorTreinoAtivoSessao.segundosTreino / 60).coerceAtLeast(1)
        val qtdSeriesEfetivas = if (totalConcluidas > 0) totalConcluidas else seriesEstado.size

        Dialog(
            onDismissRequest = { if (!salvandoSessao) exibirModalConcluirTreino = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 20.dp),
                shape = RoundedCornerShape(26.dp),
                color = SuperficieCard
            ) {
                LazyColumn(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VerdeEsmeralda)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Concluir Treino",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 19.sp,
                                    color = TextoPrimario
                                )
                            }
                            IconButton(
                                onClick = { if (!salvandoSessao) exibirModalConcluirTreino = false }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextoSecundario)
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FundoAcademia,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Duração", fontSize = 11.sp, color = TextoSecundario)
                                    Text(
                                        "$duracaoMin min",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = TextoPrimario
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Séries", fontSize = 11.sp, color = TextoSecundario)
                                    Text(
                                        "$qtdSeriesEfetivas",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = VerdeEsmeralda
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Volume Total", fontSize = 11.sp, color = TextoSecundario)
                                    Text(
                                        "${volumeAtual.toInt()} kg",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = CianoNeon
                                    )
                                }
                            }
                        }
                    }

                    if (prsBatidosNaSessao.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = AmbarDestaque.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "🏆 NOVOS RECORDES PESSOAIS (PRs) BATIDOS HOJE!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AmbarDestaque
                                    )
                                    prsBatidosNaSessao.forEach { (nomeEx, peso, reps) ->
                                        Text(
                                            "• $nomeEx: ${formatarPeso(peso)} kg × $reps reps",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextoPrimario
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Como você se sentiu no treino hoje?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextoPrimario
                            )
                            val opcoesFeeling = listOf(
                                "Insano 🔥" to "Insano",
                                "Ótimo 💪" to "Ótimo",
                                "Normal 👍" to "Normal",
                                "Pesado 🥵" to "Cansado"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                opcoesFeeling.forEach { (rotulo, valor) ->
                                    val selecionado = feelingEscolhido == valor
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selecionado) VerdeEsmeralda.copy(alpha = 0.22f) else FundoAcademia,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { feelingEscolhido = valor }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = rotulo,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (selecionado) VerdeEsmeraldaClaro else TextoSecundario,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Foto Pós-Treino",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextoPrimario
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { cameraPosTreinoLauncher.launch() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = VerdeEsmeralda, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tirar Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { galeriaPosTreinoLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Galeria", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (fotoPosTreinoBitmap != null) {
                                    Image(
                                        bitmap = fotoPosTreinoBitmap!!.asImageBitmap(),
                                        contentDescription = "Foto Pós-Treino",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = notasSessao,
                            onValueChange = { notasSessao = it },
                            label = { Text("Observações / Anotações (ex: ótimo pump, subi carga...)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = FundoAcademia,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { enviarResumoWhatsapp = !enviarResumoWhatsapp }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = VerdeEsmeralda,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "Enviar Resumo + PRs + Pós-Treino no WhatsApp",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextoPrimario
                                        )
                                        Text(
                                            "Notificação automática via Evolution API ao concluir",
                                            fontSize = 10.sp,
                                            color = TextoSecundario
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = enviarResumoWhatsapp,
                                    onCheckedChange = { enviarResumoWhatsapp = it }
                                )
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                val listaResumoCard = seriesAgrupadas.map { (chave, lista) ->
                                    ResumoExercicioCard(
                                        nome = chave.second,
                                        qtdSeries = lista.size,
                                        maiorCargaKg = lista.maxOfOrNull { it.weightKg.toDoubleOrNull() ?: 0.0 } ?: 0.0
                                    )
                                }
                                GeradorCardTreinoStories.gerarECompartilharCard(
                                    context = contexto,
                                    nomeAtleta = perfil?.name ?: "Atleta",
                                    tituloTreino = fichaAtual?.name ?: "Sessão de Treino",
                                    duracaoMinutos = duracaoMin,
                                    seriesConcluidas = qtdSeriesEfetivas,
                                    volumeTotalKg = volumeAtual.toInt(),
                                    exercicios = listaResumoCard,
                                    fotoFundo = fotoPosTreinoBitmap
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Gerar Card do Treino p/ Stories (Estilo Strava/Hevy)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = CianoNeon
                            )
                        }
                    }

                    if (feedbackSalvo != null) {
                        item {
                            Text(
                                text = feedbackSalvo!!,
                                color = VerdeEsmeralda,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    item {
                        Button(
                            enabled = !salvandoSessao,
                            onClick = {
                                salvandoSessao = true
                                escopo.launch {
                                    val urlFoto = if (fotoPosTreinoBitmap != null) {
                                        RepositorioTreinoria.enviarFotoPosTreinoStorage(fotoPosTreinoBitmap!!)
                                    } else null

                                    val concluidas = seriesEstado.filter { it.completed }.ifEmpty { seriesEstado }.map {
                                        SerieSessaoDto(
                                            exerciseId = it.exerciseId,
                                            setNumber = it.setNumber,
                                            weightKg = it.weightKg.toDoubleOrNull() ?: 0.0,
                                            reps = it.reps.toIntOrNull() ?: 10,
                                            isWarmup = it.isWarmup,
                                            isCompleted = true
                                        )
                                    }

                                    val sessao = RepositorioTreinoria.salvarSessaoTreinoConcluida(
                                        planId = fichaAtual?.id,
                                        titulo = fichaAtual?.name ?: "Sessão de Treino",
                                        duracaoSegundos = GerenciadorTreinoAtivoSessao.segundosTreino.coerceAtLeast(60),
                                        feeling = feelingEscolhido,
                                        notas = notasSessao,
                                        fotoUrl = urlFoto,
                                        resumoConselhoIa = GerenciadorTreinoAtivoSessao.resumoIaAplicadoBanner,
                                        prontidaoIa = GerenciadorTreinoAtivoSessao.prontidaoIaAplicada,
                                        seriesConcluidas = concluidas
                                    )

                                    val gravouHealth = GerenciadorHealthConnect.gravarTreinoConcluido(
                                        context = contexto,
                                        tituloTreino = fichaAtual?.name ?: "Treino de Musculação",
                                        duracaoSegundos = GerenciadorTreinoAtivoSessao.segundosTreino.coerceAtLeast(60),
                                        volumeTotalKg = sessao?.totalVolumeKg ?: volumeAtual,
                                        notas = notasSessao
                                    )

                                    val fone = perfil?.whatsappPhone
                                    if (enviarResumoWhatsapp && !fone.isNullOrBlank() && sessao != null) {
                                        val blocoPrs = if (prsBatidosNaSessao.isNotEmpty()) {
                                            "\n🏆 *Novos PRs Batidos Hoje:*\n" +
                                                prsBatidosNaSessao.joinToString("\n") { "• ${it.first}: *${formatarPeso(it.second)} kg × ${it.third} reps*" } + "\n"
                                        } else ""

                                        val blocoPosTreino = if (!fichaAtual?.postWorkoutMeal.isNullOrBlank()) {
                                            "\n🥤 *Sugestão Pós-Treino:* ${fichaAtual?.postWorkoutMeal}\n"
                                        } else ""

                                        RepositorioTreinoria.enviarMensagemWhatsapp(
                                            numero = fone,
                                            texto = "🏋️ *TREINORIA PRO • Treino Concluído!*\n\n" +
                                                "Parabéns, *${perfil?.name ?: "Atleta"}*!\n" +
                                                "📋 Ficha: *${sessao.title}*\n" +
                                                "⏱️ Duração: *${sessao.durationMinutes ?: duracaoMin} min*\n" +
                                                "✅ Séries concluídas: *${concluidas.size}*\n" +
                                                "🔥 Volume Total: *${sessao.totalVolumeKg.toInt()} kg*\n" +
                                                "💪 Sensação: *$feelingEscolhido*" +
                                                blocoPrs +
                                                blocoPosTreino +
                                                "\n🔗 Histórico completo: https://treinoria.raevan.com.br"
                                        )
                                    }

                                    salvandoSessao = false
                                    feedbackSalvo = if (gravouHealth) {
                                        "✅ Treino salvo e sincronizado com o Health Connect!"
                                    } else {
                                        "✅ Treino salvo com sucesso!"
                                    }
                                    delay(900)
                                    exibirModalConcluirTreino = false
                                    GerenciadorTreinoAtivoSessao.resetarTreinoAtivo()
                                    aoConcluirTreino()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            if (salvandoSessao) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Salvando Treino...", fontWeight = FontWeight.Black)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Salvar e Finalizar Treino", fontWeight = FontWeight.Black, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Check-in Pré-Treino com Personal IA
    if (GerenciadorTreinoAtivoSessao.exibirCheckinPreTreinoModal) {
        Dialog(
            onDismissRequest = { GerenciadorTreinoAtivoSessao.exibirCheckinPreTreinoModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 18.dp),
                shape = RoundedCornerShape(24.dp),
                color = SuperficieCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VerdeEsmeralda),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FundoAcademia)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Personal Trainer IA • Check-in do Dia",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = TextoPrimario
                                )
                                Text(
                                    text = "Adapta cargas, séries, reps e exercícios usando todo o seu histórico",
                                    fontSize = 11.sp,
                                    color = TextoSecundario
                                )
                            }
                        }
                        TextButton(onClick = { GerenciadorTreinoAtivoSessao.exibirCheckinPreTreinoModal = false }) {
                            Text("Pular ✕", fontWeight = FontWeight.Bold, color = TextoSecundario)
                        }
                    }

                    HorizontalDivider(color = BordaCard.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mensagensChatPreTreino) { msg ->
                            val ehUser = msg.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (ehUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (ehUser) VerdeEsmeralda else FundoAcademia,
                                    modifier = Modifier.widthIn(max = 310.dp)
                                ) {
                                    Text(
                                        text = msg.content,
                                        fontSize = 13.sp,
                                        color = if (ehUser) FundoAcademia else TextoPrimario,
                                        fontWeight = if (ehUser) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        if (consultandoCheckinIa) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = FundoAcademia,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            color = VerdeEsmeralda,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Analisando seu histórico completo, mapa de fadiga, medidas, sono e recalibrando séries...",
                                            fontSize = 12.sp,
                                            color = VerdeEsmeraldaClaro
                                        )
                                    }
                                }
                            }
                        }

                        if (mensagensChatPreTreino.size <= 1 && !consultandoCheckinIa) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = FundoAcademia,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "⚡ RESPOSTA RÁPIDA (Toque para marcar ou digite abaixo):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = CianoNeon
                                        )

                                        Text("🍽️ 1. Alimentação Pré-Treino:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "Refeição completa (Carbo + Proteína)",
                                                "Lanche leve / Banana / Café",
                                                "Treinando em Jejum",
                                                "Comi pouco / Correria"
                                            ).forEach { opt ->
                                                FilterChip(
                                                    selected = chipRefeicaoEscolhido == opt,
                                                    onClick = { chipRefeicaoEscolhido = if (chipRefeicaoEscolhido == opt) "" else opt },
                                                    label = { Text(opt, fontSize = 11.sp) }
                                                )
                                            }
                                        }

                                        Text("🏃 2. Aeróbico / Cardio Hoje:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "Não fiz aeróbico antes",
                                                "Já fiz 20-30min de cardio antes",
                                                "Já fiz cardio intenso hoje",
                                                "Vou fazer 20min de cardio no pós-treino"
                                            ).forEach { opt ->
                                                FilterChip(
                                                    selected = chipCardioEscolhido == opt,
                                                    onClick = { chipCardioEscolhido = if (chipCardioEscolhido == opt) "" else opt },
                                                    label = { Text(opt, fontSize = 11.sp) }
                                                )
                                            }
                                        }

                                        Text("🔋 3. Energia & Disposição:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "100% descansado (Quero progredir carga!)",
                                                "Energia normal",
                                                "Dormi pouco / Cansado",
                                                "Treino rápido (40 min)"
                                            ).forEach { opt ->
                                                FilterChip(
                                                    selected = chipEnergiaEscolhido == opt,
                                                    onClick = { chipEnergiaEscolhido = if (chipEnergiaEscolhido == opt) "" else opt },
                                                    label = { Text(opt, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (GerenciadorTreinoAtivoSessao.propostaAjusteIa != null) {
                            val prop = GerenciadorTreinoAtivoSessao.propostaAjusteIa!!
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = VerdeEsmeralda.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🏋️ PLANO ADAPTADO (${prop.adjustedBlocks.size} exercícios)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                color = VerdeEsmeralda
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = VerdeEsmeralda.copy(alpha = 0.25f)
                                            ) {
                                                Text(
                                                    text = "Prontidão: ${prop.readinessScore}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = VerdeEsmeraldaClaro,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "🎯 ${prop.workoutSummaryAdvice}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextoPrimario
                                        )

                                        prop.adjustedBlocks.forEachIndexed { idx, b ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = FundoAcademia,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${idx + 1}. ${b.exerciseName}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        if (b.changeBadge.isNotBlank()) {
                                                            Text(
                                                                text = "✨ ${b.changeBadge}",
                                                                fontSize = 10.sp,
                                                                color = VerdeEsmeraldaClaro,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${b.targetSets}×${b.targetReps} • ${formatarPeso(b.targetWeightKg)}kg",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp,
                                                        color = CianoNeon
                                                    )
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val mapaCatalogo = catalogoExercicios.associateBy { it.id }
                                                seriesEstado.clear()
                                                prop.adjustedBlocks.forEach { b ->
                                                    val exCat = mapaCatalogo[b.exerciseId]
                                                        ?: catalogoExercicios.firstOrNull {
                                                            it.name.equals(b.exerciseName, ignoreCase = true)
                                                        }
                                                    val realExId = exCat?.id ?: b.exerciseId
                                                    val hist = estatisticasExercicios[realExId]
                                                    val repsNum = b.targetReps.split("-").lastOrNull()?.filter { it.isDigit() }?.ifBlank { "10" } ?: "10"
                                                    for (s in 1..b.targetSets.coerceIn(1, 8)) {
                                                        seriesEstado.add(
                                                            SerieEditavel(
                                                                exerciseId = realExId,
                                                                exerciseName = exCat?.name ?: b.exerciseName,
                                                                muscleGroup = exCat?.muscleGroup ?: "Musculação",
                                                                equipment = exCat?.equipment ?: "Aparelho / Livre",
                                                                videoUrl = exCat?.videoUrl,
                                                                instructions = b.notes.ifBlank { exCat?.instructions },
                                                                targetRepsRange = b.targetReps,
                                                                setNumber = s,
                                                                weightKg = formatarPeso(b.targetWeightKg),
                                                                reps = repsNum,
                                                                restSeconds = b.restSeconds.takeIf { it > 0 } ?: 90,
                                                                prevWeightKg = hist?.lastWeightKg,
                                                                prevReps = hist?.lastReps,
                                                                maxPrWeightKg = hist?.maxWeightKg,
                                                                aiBadge = b.changeBadge
                                                            )
                                                        )
                                                    }
                                                }
                                                exerciciosExpandidosIds.clear()
                                                seriesEstado.firstOrNull()?.exerciseId?.let { exerciciosExpandidosIds.add(it) }
                                                GerenciadorTreinoAtivoSessao.resumoIaAplicadoBanner = "✨ ${prop.workoutSummaryAdvice} (Prontidão ${prop.readinessScore}%)"
                                                GerenciadorTreinoAtivoSessao.prontidaoIaAplicada = prop.readinessScore
                                                GerenciadorTreinoAtivoSessao.exibirCheckinPreTreinoModal = false
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Aplicar Cargas, Séries e Exercícios no Treino",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textoLivreCheckin,
                            onValueChange = { textoLivreCheckin = it },
                            placeholder = {
                                Text(
                                    if (chipRefeicaoEscolhido.isNotBlank() || chipCardioEscolhido.isNotBlank() || chipEnergiaEscolhido.isNotBlank())
                                        "Algo mais? (ex: troca supino por halteres)..."
                                    else
                                        "Conte o que comeu, cardio ou peça ajustes...",
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        val podeEnviar = !consultandoCheckinIa &&
                            (textoLivreCheckin.isNotBlank() || chipRefeicaoEscolhido.isNotBlank() || chipCardioEscolhido.isNotBlank() || chipEnergiaEscolhido.isNotBlank())

                        Button(
                            enabled = podeEnviar,
                            onClick = {
                                val partes = mutableListOf<String>()
                                if (chipRefeicaoEscolhido.isNotBlank()) partes.add("Alimentação pré-treino: $chipRefeicaoEscolhido.")
                                if (chipCardioEscolhido.isNotBlank()) partes.add("Aeróbico hoje: $chipCardioEscolhido.")
                                if (chipEnergiaEscolhido.isNotBlank()) partes.add("Nível de energia: $chipEnergiaEscolhido.")
                                if (textoLivreCheckin.isNotBlank()) partes.add(textoLivreCheckin.trim())

                                val msgFinal = partes.joinToString(" ")
                                val copiaHistorico = mensagensChatPreTreino.toList()
                                mensagensChatPreTreino.add(MensagemChatIa("user", msgFinal))
                                textoLivreCheckin = ""
                                chipRefeicaoEscolhido = ""
                                chipCardioEscolhido = ""
                                chipEnergiaEscolhido = ""
                                consultandoCheckinIa = true

                                val blocosAtuais = seriesEstado.groupBy { it.exerciseId }.map { (exId, lista) ->
                                    val prim = lista.first()
                                    BlocoExercicioAjustadoIa(
                                        exerciseId = exId,
                                        exerciseName = prim.exerciseName,
                                        targetSets = lista.size,
                                        targetReps = prim.targetRepsRange,
                                        targetWeightKg = prim.weightKg.toDoubleOrNull() ?: 20.0,
                                        restSeconds = prim.restSeconds,
                                        notes = prim.instructions ?: "",
                                        changeBadge = "Atual"
                                    )
                                }

                                escopo.launch {
                                    val resp = RepositorioTreinoria.consultarCheckinPreTreinoIa(
                                        context = contexto,
                                        tituloTreino = fichaAtual?.name ?: "Treino do Dia",
                                        blocosAtuais = blocosAtuais,
                                        catalogoExercicios = catalogoExercicios,
                                        historicoChat = copiaHistorico,
                                        mensagemAtleta = msgFinal
                                    )
                                    mensagensChatPreTreino.add(MensagemChatIa("assistant", resp.replyMessage))
                                    GerenciadorTreinoAtivoSessao.propostaAjusteIa = resp
                                    consultandoCheckinIa = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Enviar")
                        }
                    }
                }
            }
        }
    }

    // Modal Trocar por Exercício Equivalente
    if (exercicioIdParaTrocarEquivalente != null) {
        val serieOrigem = seriesEstado.firstOrNull { it.exerciseId == exercicioIdParaTrocarEquivalente }
        val grupoAlvo = serieOrigem?.muscleGroup ?: ""
        val equivalentes = catalogoExercicios.filter {
            it.muscleGroup.equals(grupoAlvo, ignoreCase = true) && it.id != exercicioIdParaTrocarEquivalente
        }

        AlertDialog(
            onDismissRequest = { exercicioIdParaTrocarEquivalente = null },
            title = {
                Column {
                    Text("🔄 Trocar por Equivalente", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "Aparelho ocupado? Escolha outro exercício de $grupoAlvo:",
                        fontSize = 12.sp,
                        color = CianoNeon
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(equivalentes, key = { it.id }) { eq ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SuperficieElevada,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val hist = estatisticasExercicios[eq.id]
                                    seriesEstado.indices.forEach { i ->
                                        if (seriesEstado[i].exerciseId == exercicioIdParaTrocarEquivalente) {
                                            seriesEstado[i] = seriesEstado[i].copy(
                                                exerciseId = eq.id,
                                                exerciseName = eq.name,
                                                muscleGroup = eq.muscleGroup,
                                                equipment = eq.equipment,
                                                videoUrl = eq.videoUrl,
                                                instructions = eq.instructions,
                                                prevWeightKg = hist?.lastWeightKg,
                                                prevReps = hist?.lastReps,
                                                maxPrWeightKg = hist?.maxWeightKg
                                            )
                                        }
                                    }
                                    exerciciosExpandidosIds.remove(exercicioIdParaTrocarEquivalente)
                                    exerciciosExpandidosIds.add(eq.id)
                                    exercicioIdParaTrocarEquivalente = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(eq.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${eq.muscleGroup} • ${eq.equipment}", fontSize = 11.sp, color = TextoSecundario)
                                }
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = AmbarDestaque)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exercicioIdParaTrocarEquivalente = null }) { Text("Cancelar") }
            }
        )
    }

    // Modal Adicionar Exercício Extra ao Treino de Hoje
    if (exibirAdicionarExercicioModal) {
        val filtrados = catalogoExercicios.filter {
            buscaAdicionarExercicio.isBlank() ||
                it.name.contains(buscaAdicionarExercicio, ignoreCase = true) ||
                it.muscleGroup.contains(buscaAdicionarExercicio, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { exibirAdicionarExercicioModal = false },
            title = { Text("➕ Adicionar Exercício ao Treino", fontWeight = FontWeight.Black, fontSize = 17.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = buscaAdicionarExercicio,
                        onValueChange = { buscaAdicionarExercicio = it },
                        label = { Text("Buscar por nome ou músculo...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtrados, key = { it.id }) { ex ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuperficieElevada,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val hist = estatisticasExercicios[ex.id]
                                        val pesoIni = if ((hist?.lastWeightKg ?: 0.0) > 0) formatarPeso(hist!!.lastWeightKg) else "20"
                                        val repsIni = (hist?.lastReps ?: 10).toString()
                                        for (s in 1..3) {
                                            seriesEstado.add(
                                                SerieEditavel(
                                                    exerciseId = ex.id,
                                                    exerciseName = ex.name,
                                                    muscleGroup = ex.muscleGroup,
                                                    equipment = ex.equipment,
                                                    videoUrl = ex.videoUrl,
                                                    instructions = ex.instructions,
                                                    targetRepsRange = "8-12",
                                                    setNumber = s,
                                                    weightKg = pesoIni,
                                                    reps = repsIni,
                                                    restSeconds = 90,
                                                    prevWeightKg = hist?.lastWeightKg,
                                                    prevReps = hist?.lastReps,
                                                    maxPrWeightKg = hist?.maxWeightKg
                                                )
                                            )
                                        }
                                        if (GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) exerciciosExpandidosIds.clear()
                                        exerciciosExpandidosIds.add(ex.id)
                                        exibirAdicionarExercicioModal = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${ex.muscleGroup} • ${ex.equipment}", fontSize = 11.sp, color = TextoSecundario)
                                    }
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = VerdeEsmeralda)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exibirAdicionarExercicioModal = false }) { Text("Fechar") }
            }
        )
    }

    // Modal Calculadora de Anilhas
    if (exibirCalculadoraAnilhas) {
        val total = pesoAlvoCalculadora.toDoubleOrNull() ?: 60.0
        val barra = pesoBarraCalculadora.toDoubleOrNull() ?: 20.0
        val porLado = ((total - barra) / 2.0).coerceAtLeast(0.0)

        val anilhasDisponiveis = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        val combinacao = remember(porLado) {
            var restante = porLado
            val lista = mutableListOf<String>()
            for (a in anilhasDisponiveis) {
                val qtd = (restante / a).toInt()
                if (qtd > 0) {
                    lista.add("${qtd}x ${formatarPeso(a)}kg")
                    restante -= qtd * a
                }
            }
            lista
        }

        AlertDialog(
            onDismissRequest = { exibirCalculadoraAnilhas = false },
            title = {
                Column {
                    Text("🧮 Calculadora de Anilhas", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    if (nomeExercicioCalculadora.isNotBlank()) {
                        Text(nomeExercicioCalculadora, fontSize = 12.sp, color = CianoNeon)
                    }
                }
            },
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
                                "Em CADA LADO da barra: ${formatarPeso(porLado)} kg",
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
                Button(onClick = { exibirCalculadoraAnilhas = false }) { Text("Fechar") }
            }
        )
    }

    // Modal Seletor Rápido de CARGA (kg)
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(185.dp)
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
                                            color = if (selecionado) FundoAcademia else TextoPrimario,
                                            maxLines = 1
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

    // Modal Seletor Rápido de REPETIÇÕES
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
                                            color = if (selecionado) FundoAcademia else TextoPrimario,
                                            maxLines = 1
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
                        indiceSerieCameraAoVivoIa = null
                        GerenciadorTreinoAtivoSessao.resetarTreinoAtivo()
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

    val iniciarSerieEmTelaInteira = { idxSerie: Int ->
        val serie = seriesEstado.getOrNull(idxSerie)
        if (serie != null) {
            if (GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) {
                exerciciosExpandidosIds.clear()
                exerciciosExpandidosIds.add(serie.exerciseId)
            }
            val limiteReps = (serie.reps.toIntOrNull() ?: 10).coerceIn(1, 50)
            alvoRepsTelaInteira = limiteReps
            repAtualTelaInteira = 1
            descansoTotalTelaInteira = serie.restSeconds.coerceAtLeast(15)
            descansoRestanteTelaInteira = 0
            faseTelaInteira = FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES
            indiceSerieTelaInteira = idxSerie
        }
    }

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
            verificarAutoAvancoExercicio(serieAtual.exerciseId)
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
                            .padding(horizontal = 22.dp, vertical = 22.dp),
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
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(230.dp)
                                            .clip(CircleShape)
                                            .background(VerdeEsmeralda.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$repAtualTelaInteira",
                                                fontSize = 88.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VerdeEsmeralda
                                            )
                                            Text(
                                                text = "de $alvoRepsTelaInteira reps",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextoSecundario
                                            )
                                        }
                                    }

                                    Text(
                                        text = labelFaseMovimento,
                                        fontSize = 32.sp,
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
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(220.dp)
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
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (descansoRestanteTelaInteira > 0) AmbarDestaque else VerdeEsmeralda
                                            )
                                            Text(
                                                text = "${descansoRestanteTelaInteira}s",
                                                fontSize = 78.sp,
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
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                color = TextoPrimario,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "${proximaSerie.weightKg} kg × ${proximaSerie.reps} repetições",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VerdeEsmeraldaClaro
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Todas as séries desta ficha foram concluídas! 🏆",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VerdeEsmeralda,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BarraControleMidiaTelaCheia()

                            when (faseTelaInteira) {
                                FaseExecucaoTelaInteira.EXECUTANDO_REPETICOES -> {
                                    Button(
                                        onClick = {
                                            if (idxAtual in seriesEstado.indices) {
                                                seriesEstado[idxAtual] = seriesEstado[idxAtual].copy(completed = true)
                                            }
                                            verificarAutoAvancoExercicio(serieAtual.exerciseId)
                                            descansoTotalTelaInteira = serieAtual.restSeconds.coerceAtLeast(15)
                                            descansoRestanteTelaInteira = descansoTotalTelaInteira
                                            faseTelaInteira = FaseExecucaoTelaInteira.EM_DESCANSO
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                        modifier = Modifier.fillMaxWidth().height(58.dp)
                                    ) {
                                        Text(
                                            "Concluir Série & Iniciar Descanso",
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
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(58.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Iniciar Próxima (${proximaSerie.setNumber}ª Série • ${proximaSerie.reps} reps)",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                indiceSerieTelaInteira = null
                                                exibirModalHumorPosTreino = true
                                            },
                                            shape = RoundedCornerShape(18.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            modifier = Modifier.fillMaxWidth().height(58.dp)
                                        ) {
                                            Text("Abrir Resumo & Concluir Treino", fontWeight = FontWeight.Black, fontSize = 15.sp)
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
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
                                text = "TREINO DO DIA • ${nomeDiaHoje.uppercase()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = CianoNeon
                            )
                            Text(
                                text = fichaAtual?.name ?: "Carregando Treino do Dia...",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = VerdeEsmeralda
                            )
                            Text(
                                text = "⏱️ ${GerenciadorTreinoAtivoSessao.segundosTreino / 60}m ${GerenciadorTreinoAtivoSessao.segundosTreino % 60}s • $totalConcluidas/${seriesEstado.size} séries • ${volumeAtual.toInt()} kg",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { GerenciadorTreinoAtivoSessao.exibirCheckinPreTreinoModal = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VerdeEsmeralda, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Coach IA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = VerdeEsmeralda)
                        }

                        OutlinedButton(
                            onClick = { exibirConfirmacaoDescartar = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = VermelhoAlerta, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descartar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VermelhoAlerta)
                        }

                        Button(
                            onClick = { exibirModalHumorPosTreino = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Concluir", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Barra de Controle de Expansão/Compressão das Séries (Modo Foco 1 Exercício vs Expandir Todos)
                    HorizontalDivider(color = BordaCard.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido,
                            onClick = {
                                GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido =
                                    !GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido
                                if (GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) {
                                    val emExecucaoId = seriesEstado.firstOrNull { !it.completed }?.exerciseId
                                        ?: seriesEstado.firstOrNull()?.exerciseId
                                    exerciciosExpandidosIds.clear()
                                    if (emExecucaoId != null) exerciciosExpandidosIds.add(emExecucaoId)
                                }
                            },
                            label = {
                                Text(
                                    if (GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido) "🎯 Foco: 1 Exercício por Vez" else "🎯 Foco (1 por vez)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    GerenciadorTreinoAtivoSessao.modoFocoApenasUmExpandido = false
                                    exerciciosExpandidosIds.clear()
                                    seriesAgrupadas.keys.forEach { exerciciosExpandidosIds.add(it.first) }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Expandir Todos", fontSize = 11.sp, color = CianoNeon, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    exerciciosExpandidosIds.clear()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Recolher Todos", fontSize = 11.sp, color = TextoSecundario, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (GerenciadorTreinoAtivoSessao.resumoIaAplicadoBanner != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerdeEsmeralda.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = GerenciadorTreinoAtivoSessao.resumoIaAplicadoBanner!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerdeEsmeraldaClaro,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = BordaCard.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (GerenciadorTreinoAtivoSessao.descansoBarraRapida > 0) AmbarDestaque else TextoSecundario,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (GerenciadorTreinoAtivoSessao.descansoBarraRapida > 0) "Descanso: ${GerenciadorTreinoAtivoSessao.descansoBarraRapida}s" else "Descanso:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (GerenciadorTreinoAtivoSessao.descansoBarraRapida > 0) AmbarDestaque else TextoSecundario
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(45, 60, 90, 120).forEach { seg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuperficieElevada,
                                    modifier = Modifier.clickable {
                                        GerenciadorTreinoAtivoSessao.exercicioDescansoBarra = "Próxima Série"
                                        GerenciadorTreinoAtivoSessao.descansoBarraRapida = seg
                                    }
                                ) {
                                    Text(
                                        text = "${seg}s",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CianoNeon,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (GerenciadorTreinoAtivoSessao.descansoBarraRapida > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VermelhoAlerta.copy(alpha = 0.2f),
                                    modifier = Modifier.clickable { GerenciadorTreinoAtivoSessao.descansoBarraRapida = 0 }
                                ) {
                                    Text(
                                        text = "Zerar",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VermelhoAlerta,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lista de Exercícios (Comprimir e Expandir Séries ao tocar no cabeçalho!)
        seriesAgrupadas.forEach { (chave, seriesDoExercicio) ->
            val (exId, nomeExercicio) = chave
            val primeiro = seriesDoExercicio.firstOrNull()
            val videoUrl = primeiro?.videoUrl
            val grupoMuscular = primeiro?.muscleGroup ?: ""
            val equipamento = primeiro?.equipment ?: ""
            val instrucoes = primeiro?.instructions
            val prMaximo = primeiro?.maxPrWeightKg
            val aiBadge = primeiro?.aiBadge
            val feitasCount = seriesDoExercicio.count { it.completed }
            val todasConcluidas = feitasCount == seriesDoExercicio.size && seriesDoExercicio.isNotEmpty()
            val estaExpandido = exerciciosExpandidosIds.contains(exId)
            val videoAbertoNesteCard = exercicioComVideoAbertoId == exId

            item(key = exId) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (estaExpandido) {
                                Modifier.border(1.5.dp, VerdeEsmeralda.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // CABEÇALHO CLICÁVEL DO EXERCÍCIO (Toque para Expandir ou Comprimir as Séries!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alternarExpansaoExercicio(exId) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (grupoMuscular.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = VerdeEsmeralda.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = grupoMuscular.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VerdeEsmeralda,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (equipamento.isNotBlank()) {
                                        Text(
                                            text = "• $equipamento",
                                            fontSize = 11.sp,
                                            color = TextoSecundario
                                        )
                                    }
                                    if (prMaximo != null && prMaximo > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AmbarDestaque.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "🏆 PR ${formatarPeso(prMaximo)}kg",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmbarDestaque,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = nomeExercicio,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = if (todasConcluidas) VerdeEsmeraldaClaro else TextoPrimario
                                )

                                if (!aiBadge.isNullOrBlank()) {
                                    Text(
                                        text = "🤖 Ajuste IA: $aiBadge",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CianoNeon
                                    )
                                }

                                Text(
                                    text = if (estaExpandido)
                                        "Meta: ${seriesDoExercicio.size} séries × ${primeiro?.targetRepsRange ?: "8-12"} reps • Toque p/ recolher ▲"
                                    else
                                        "Séries recolhidas (${primeiro?.weightKg ?: "20"}kg × ${primeiro?.reps ?: "10"}r) • Toque p/ expandir ▼",
                                    fontSize = 11.sp,
                                    color = if (estaExpandido) VerdeEsmeraldaClaro else CianoNeon
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (todasConcluidas) VerdeEsmeralda.copy(alpha = 0.22f) else SuperficieElevada
                                ) {
                                    Text(
                                        text = if (todasConcluidas) "✓ $feitasCount/${seriesDoExercicio.size}" else "$feitasCount/${seriesDoExercicio.size}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (todasConcluidas) VerdeEsmeralda else TextoPrimario,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                Icon(
                                    imageVector = if (estaExpandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (estaExpandido) "Comprimir séries" else "Expandir séries",
                                    tint = if (estaExpandido) VerdeEsmeralda else TextoSecundario,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // CONTEÚDO EXPANSÍVEL DO EXERCÍCIO (Botões de Ação + Lista de Séries)
                        AnimatedVisibility(visible = estaExpandido) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CianoNeon.copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .clickable {
                                                val serieNaoConcluida = seriesDoExercicio.firstOrNull { !it.completed } ?: seriesDoExercicio.firstOrNull()
                                                if (serieNaoConcluida != null) {
                                                    val idxAlvo = seriesEstado.indexOf(serieNaoConcluida)
                                                    if (idxAlvo >= 0) indiceSerieCameraAoVivoIa = idxAlvo
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Câmera IA Live",
                                                tint = CianoNeon,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Câmera IA",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = CianoNeon,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (!videoUrl.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = VerdeEsmeralda.copy(alpha = 0.14f),
                                            modifier = Modifier
                                                .weight(0.95f)
                                                .clickable {
                                                    exercicioComVideoAbertoId = if (videoAbertoNesteCard) null else exId
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (videoAbertoNesteCard) Icons.Default.Close else Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = VerdeEsmeralda,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (videoAbertoNesteCard) "Fechar" else "Vídeo",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VerdeEsmeralda,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = AmbarDestaque.copy(alpha = 0.14f),
                                        modifier = Modifier
                                            .weight(1.05f)
                                            .clickable {
                                                exercicioIdParaTrocarEquivalente = exId
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = null,
                                                tint = AmbarDestaque,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Trocar",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AmbarDestaque,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SuperficieElevada,
                                        modifier = Modifier
                                            .weight(0.95f)
                                            .clickable {
                                                pesoAlvoCalculadora = primeiro?.weightKg ?: "60"
                                                nomeExercicioCalculadora = nomeExercicio
                                                exibirCalculadoraAnilhas = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Calculate,
                                                contentDescription = null,
                                                tint = TextoPrimario,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Anilhas",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextoPrimario,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

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
                                        color = if (serie.completed) VerdeEsmeralda.copy(alpha = 0.10f) else SuperficieElevada.copy(alpha = 0.65f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = when {
                                                            serie.isWarmup -> AmbarDestaque.copy(alpha = 0.22f)
                                                            serie.completed -> VerdeEsmeralda
                                                            else -> FundoAcademia
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clickable {
                                                                if (idxGlobal >= 0) {
                                                                    seriesEstado[idxGlobal] = serie.copy(isWarmup = !serie.isWarmup)
                                                                }
                                                            }
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = if (serie.isWarmup) "W" else "${serie.setNumber}ª",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 12.sp,
                                                                color = when {
                                                                    serie.isWarmup -> AmbarDestaque
                                                                    serie.completed -> FundoAcademia
                                                                    else -> TextoPrimario
                                                                },
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    Column {
                                                        Text(
                                                            text = "ANTERIOR",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextoSecundario
                                                        )
                                                        Text(
                                                            text = if (serie.prevWeightKg != null && serie.prevWeightKg > 0)
                                                                "${formatarPeso(serie.prevWeightKg)}kg × ${serie.prevReps ?: 10}"
                                                            else
                                                                "Sem hist.",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextoPrimario,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = CianoNeon.copy(alpha = 0.18f),
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .clickable {
                                                                if (idxGlobal >= 0) {
                                                                    indiceSerieCameraAoVivoIa = idxGlobal
                                                                }
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Videocam,
                                                                contentDescription = "Câmera IA Live",
                                                                tint = CianoNeon,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "Live IA",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = CianoNeon,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = RoxoDestaque.copy(alpha = 0.18f),
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .clickable {
                                                                if (idxGlobal >= 0) {
                                                                    iniciarSerieEmTelaInteira(idxGlobal)
                                                                }
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Iniciar Contagem Tela Cheia",
                                                                tint = RoxoDestaque,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = "${serie.reps}r",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = RoxoDestaque,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    if (seriesDoExercicio.size > 1) {
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = FundoAcademia,
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clickable {
                                                                    if (idxGlobal >= 0) {
                                                                        seriesEstado.removeAt(idxGlobal)
                                                                        var num = 1
                                                                        seriesEstado.indices.forEach { i ->
                                                                            if (seriesEstado[i].exerciseId == exId) {
                                                                                seriesEstado[i] = seriesEstado[i].copy(setNumber = num++)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Remover série",
                                                                    tint = TextoSecundario,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (serie.completed) VerdeEsmeralda else FundoAcademia,
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .clickable {
                                                                if (idxGlobal >= 0) {
                                                                    val novoStatus = !serie.completed
                                                                    seriesEstado[idxGlobal] = serie.copy(completed = novoStatus)
                                                                    if (novoStatus) {
                                                                        verificarAutoAvancoExercicio(serie.exerciseId)
                                                                        GerenciadorTreinoAtivoSessao.exercicioDescansoBarra = serie.exerciseName
                                                                        GerenciadorTreinoAtivoSessao.descansoBarraRapida = serie.restSeconds
                                                                    }
                                                                }
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = if (serie.completed) FundoAcademia else TextoPrimario,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = if (serie.completed) "Feito" else "OK",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = if (serie.completed) FundoAcademia else TextoPrimario,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = FundoAcademia,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(SuperficieCard)
                                                                .clickable {
                                                                    if (idxGlobal >= 0) {
                                                                        val atual = serie.weightKg.toDoubleOrNull() ?: 20.0
                                                                        val novo = (atual - 2.5).coerceAtLeast(0.0)
                                                                        seriesEstado[idxGlobal] = serie.copy(weightKg = formatarPeso(novo))
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "−",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 18.sp,
                                                                color = TextoPrimario,
                                                                maxLines = 1
                                                            )
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
                                                                text = serie.weightKg,
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 16.sp,
                                                                color = TextoPrimario,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Clip
                                                            )
                                                            Text(
                                                                text = "KG",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = VerdeEsmeralda,
                                                                maxLines = 1
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(SuperficieCard)
                                                                .clickable {
                                                                    if (idxGlobal >= 0) {
                                                                        val atual = serie.weightKg.toDoubleOrNull() ?: 20.0
                                                                        val novo = atual + 2.5
                                                                        seriesEstado[idxGlobal] = serie.copy(weightKg = formatarPeso(novo))
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "+",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 18.sp,
                                                                color = VerdeEsmeralda,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = FundoAcademia,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(SuperficieCard)
                                                                .clickable {
                                                                    if (idxGlobal >= 0) {
                                                                        val atual = serie.reps.toIntOrNull() ?: 10
                                                                        val novo = (atual - 1).coerceAtLeast(1)
                                                                        seriesEstado[idxGlobal] = serie.copy(reps = novo.toString())
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "−",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 18.sp,
                                                                color = TextoPrimario,
                                                                maxLines = 1
                                                            )
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
                                                                fontSize = 16.sp,
                                                                color = TextoPrimario,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Clip
                                                            )
                                                            Text(
                                                                text = "REPS",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = CianoNeon,
                                                                maxLines = 1
                                                            )
                                                        }

                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(SuperficieCard)
                                                                .clickable {
                                                                    if (idxGlobal >= 0) {
                                                                        val atual = serie.reps.toIntOrNull() ?: 10
                                                                        val novo = atual + 1
                                                                        seriesEstado[idxGlobal] = serie.copy(reps = novo.toString())
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "+",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 18.sp,
                                                                color = VerdeEsmeralda,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = VerdeEsmeralda.copy(alpha = 0.14f),
                                        modifier = Modifier.clickable {
                                            val ultima = seriesDoExercicio.lastOrNull()
                                            if (ultima != null) {
                                                val posInserir = seriesEstado.indexOfLast { it.exerciseId == exId } + 1
                                                seriesEstado.add(
                                                    posInserir,
                                                    ultima.copy(
                                                        setNumber = seriesDoExercicio.size + 1,
                                                        completed = false
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = VerdeEsmeralda, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ Série", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeEsmeralda)
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            seriesEstado.removeAll { it.exerciseId == exId }
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = TextoSecundario, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Remover exercício", fontSize = 11.sp, color = TextoSecundario)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { exibirAdicionarExercicioModal = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = VerdeEsmeralda)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Adicionar Exercício ao Treino",
                    fontWeight = FontWeight.Bold,
                    color = VerdeEsmeralda
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exibirModalConcluirTreino = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Concluir Treino (Resumo, Foto & Card)", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = { exibirConfirmacaoDescartar = true },
                    shape = RoundedCornerShape(16.dp),
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
