package br.com.raevan.treinoria.nucleo.componentes

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.raevan.treinoria.apresentacao.treino.CadencePresetAndroid
import br.com.raevan.treinoria.dados.repositorios.AnalisePosturaAoVivoIa
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

private fun bitmapParaBase64Jpeg(bitmap: Bitmap): String {
    val escalado = Bitmap.createScaledBitmap(bitmap, 420, 320, true)
    val stream = ByteArrayOutputStream()
    escalado.compress(Bitmap.CompressFormat.JPEG, 72, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

private fun padraoArticularParaExercicio(nome: String, grupoMuscular: String): String {
    val g = grupoMuscular.lowercase()
    val n = nome.lowercase()
    return when {
        g.contains("perna") || g.contains("glúteo") || n.contains("agachamento") || n.contains("leg") || n.contains("stiff") ->
            "Ângulo do Joelho / Quadril"
        g.contains("abd") ->
            "Flexão de Tronco / Quadril"
        n.contains("elevação lateral") || n.contains("face pull") ->
            "Abdução de Ombro"
        else ->
            "Ângulo do Cotovelo / Braço"
    }
}

/**
 * Modal de Câmera Ao Vivo com Visão Computacional IA (Gemini 2.5 Vision) +
 * Interação Bidirecional em Áudio (Microfone + Voz TTS) + Controle de Mídia +
 * Contador de Repetições e Descanso In-Live — idêntico ao `AiLiveCameraTrackerModal.tsx` da Web!
 */
@Composable
fun DialogoCameraAoVivoCoachIa(
    nomeExercicio: String,
    grupoMuscular: String,
    numeroSerie: Int,
    totalSeriesDoExercicio: Int,
    alvoRepsStr: String,
    pesoKgStr: String,
    descansoSegundos: Int,
    presetCadencia: CadencePresetAndroid,
    tts: TextToSpeech?,
    vozInicialAtiva: Boolean,
    aoConcluirSerie: (Int) -> Unit,
    aoAvancarParaProximaSerie: (() -> Unit)? = null,
    proximaSerieDescricao: String? = null,
    aoFechar: () -> Unit
) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val escopo = rememberCoroutineScope()

    val alvoReps = remember(alvoRepsStr) {
        alvoRepsStr.split("-").lastOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.coerceIn(1, 50) ?: 10
    }

    var usarCameraFrontal by remember { mutableStateOf(true) }
    var vozAtiva by remember { mutableStateOf(vozInicialAtiva) }
    var contagemAutomaticaAtiva by remember { mutableStateOf(true) }
    var modoAtual by remember { mutableStateOf("executing") } // "executing" ou "resting"

    var repAtual by remember { mutableStateOf(0) }
    var faseMovimento by remember { mutableStateOf("up") } // "up" ou "down"
    var anguloArticular by remember { mutableStateOf(165) }
    var rotuloFaseVoz by remember { mutableStateOf("POSICIONE-SE") }
    var descansoRestante by remember { mutableStateOf(0) }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var analisandoFrameIa by remember { mutableStateOf(false) }
    var feedbackVisualIa by remember { mutableStateOf<AnalisePosturaAoVivoIa?>(null) }
    var ultimaPerguntaVoz by remember { mutableStateOf<String?>(null) }
    var ultimaRespostaVozIa by remember { mutableStateOf<String?>(null) }

    var temPermissaoCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val falar = { texto: String ->
        if (vozAtiva && texto.isNotBlank()) {
            try {
                tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "live_cam_${System.currentTimeMillis()}")
            } catch (_: Throwable) {}
        }
    }

    val permissaoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedida ->
        temPermissaoCamera = concedida
        if (concedida) {
            falar("Câmera IA ativa para $nomeExercicio. Posicione-se e vamos começar!")
        }
    }

    // Reconhecimento de Voz Nativo do Android para conversar por áudio com o Coach IA durante a série!
    val reconhecedorVozLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val falas = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val pergunta = falas?.firstOrNull()?.trim() ?: ""
            if (pergunta.isNotBlank()) {
                ultimaPerguntaVoz = pergunta
                analisandoFrameIa = true
                val frameBmp = try { previewViewRef?.bitmap } catch (_: Throwable) { null }
                val frameB64 = if (frameBmp != null) {
                    try { bitmapParaBase64Jpeg(frameBmp) } catch (_: Throwable) { null }
                } else null

                escopo.launch {
                    val respostaFalada = RepositorioTreinoria.conversarEmAudioComCoachNaCamera(
                        context = contexto,
                        nomeExercicio = nomeExercicio,
                        grupoMuscular = grupoMuscular,
                        numeroSerie = numeroSerie,
                        totalSeries = totalSeriesDoExercicio,
                        pesoKg = pesoKgStr,
                        repAtual = repAtual,
                        alvoReps = alvoReps,
                        anguloAtual = anguloArticular,
                        perguntaFaladaPeloAtleta = pergunta,
                        frameJpegBase64 = frameB64
                    )
                    ultimaRespostaVozIa = respostaFalada
                    analisandoFrameIa = false
                    falar(respostaFalada)
                }
            }
        }
    }

    val iniciarEscutaMicrofone = {
        try {
            val intentVoz = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "🎙️ Fale com o Coach IA (ex: 'Minha postura está boa?', 'Diminui a carga?')"
                )
            }
            reconhecedorVozLauncher.launch(intentVoz)
        } catch (_: Throwable) {
            falar("Microfone indisponível neste aparelho.")
        }
    }

    val avaliarPosturaComGeminiVision = {
        if (!analisandoFrameIa) {
            val bmp = try { previewViewRef?.bitmap } catch (_: Throwable) { null }
            if (bmp != null) {
                analisandoFrameIa = true
                ultimaRespostaVozIa = null
                escopo.launch {
                    val b64 = try { bitmapParaBase64Jpeg(bmp) } catch (_: Throwable) { "" }
                    val analise = RepositorioTreinoria.analisarPosturaCameraAoVivoComIa(
                        nomeExercicio = nomeExercicio,
                        grupoMuscular = grupoMuscular,
                        repAtual = repAtual,
                        alvoReps = alvoRepsStr,
                        anguloAtual = anguloArticular,
                        fase = if (faseMovimento == "down") "Fase Excêntrica / Contração" else "Fase Estabilizada / Topo",
                        jpegBase64 = b64
                    )
                    feedbackVisualIa = analise
                    analisandoFrameIa = false
                    if (modoAtual == "executing" && analise.shortVoiceTip.isNotBlank()) {
                        falar(analise.shortVoiceTip)
                    }
                }
            } else {
                falar("Aguardando imagem da câmera para analisar sua postura.")
            }
        }
    }

    val acionarDescansoInLive = {
        val feitas = if (repAtual > 0) repAtual else alvoReps
        aoConcluirSerie(feitas)
        modoAtual = "resting"
        descansoRestante = descansoSegundos.coerceAtLeast(15)
        GerenciadorNotificacoes.vibrarFimDescanso(contexto)
        falar("Série $numeroSerie concluída com $feitas repetições! Descanso de $descansoSegundos segundos iniciado.")
    }

    // Solicita permissão de câmera na abertura se necessário
    LaunchedEffect(Unit) {
        if (!temPermissaoCamera) {
            permissaoCameraLauncher.launch(Manifest.permission.CAMERA)
        } else {
            falar("Câmera IA ativa para $nomeExercicio. Posicione-se e inicie as repetições!")
        }
    }

    // Vincula o CameraX ao PreviewView quando a câmera ou lente muda
    LaunchedEffect(temPermissaoCamera, usarCameraFrontal, previewViewRef) {
        val pv = previewViewRef ?: return@LaunchedEffect
        if (!temPermissaoCamera) return@LaunchedEffect
        try {
            val future = ProcessCameraProvider.getInstance(contexto)
            future.addListener(
                {
                    try {
                        val cameraProvider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = pv.surfaceProvider
                        }
                        val seletor = if (usarCameraFrontal) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, seletor, preview)
                    } catch (_: Throwable) {}
                },
                ContextCompat.getMainExecutor(contexto)
            )
        } catch (_: Throwable) {}
    }

    // Motor de Cadência Guiada por Voz + Ângulo Biomecânico + Auto-Análise Visual IA no meio da série
    LaunchedEffect(modoAtual, contagemAutomaticaAtiva) {
        if (modoAtual == "executing" && contagemAutomaticaAtiva) {
            rotuloFaseVoz = "PREPARAR..."
            delay(1800)

            while (modoAtual == "executing" && contagemAutomaticaAtiva && repAtual < alvoReps) {
                val proxRep = repAtual + 1

                // Fase Excêntrica (Descida)
                faseMovimento = "down"
                falar("Desce")
                for (s in 1..presetCadencia.eccentric) {
                    if (modoAtual != "executing" || !contagemAutomaticaAtiva) break
                    anguloArticular = (160 - (s * (62 / presetCadencia.eccentric.coerceAtLeast(1)))).coerceIn(88, 165)
                    rotuloFaseVoz = "DESCE (${s}s)"
                    delay(1000)
                }

                // Pausa Isométrica
                if (presetCadencia.pause > 0 && modoAtual == "executing" && contagemAutomaticaAtiva) {
                    falar("Segura")
                    for (s in 1..presetCadencia.pause) {
                        if (modoAtual != "executing" || !contagemAutomaticaAtiva) break
                        anguloArticular = 92
                        rotuloFaseVoz = "SEGURA / CONTRAI"
                        delay(1000)
                    }
                }

                // Fase Concêntrica (Subida + Contagem da Repetição)
                if (modoAtual == "executing" && contagemAutomaticaAtiva) {
                    faseMovimento = "up"
                    repAtual = proxRep
                    anguloArticular = 162
                    rotuloFaseVoz = "SOBE! ($repAtual/$alvoReps)"
                    falar("Sobe $repAtual")

                    // Na repetição 2 (e na metade da série), dispara automaticamente uma análise de postura com Gemini Vision!
                    if (repAtual == 2 && !analisandoFrameIa && feedbackVisualIa == null) {
                        avaliarPosturaComGeminiVision()
                    }

                    for (s in 1..presetCadencia.concentric.coerceAtLeast(1)) {
                        if (modoAtual != "executing" || !contagemAutomaticaAtiva) break
                        delay(1000)
                    }
                }
            }

            if (modoAtual == "executing" && contagemAutomaticaAtiva && repAtual >= alvoReps) {
                acionarDescansoInLive()
            }
        }
    }

    // Cronômetro de Descanso In-Live
    LaunchedEffect(modoAtual, descansoRestante) {
        if (modoAtual == "resting" && descansoRestante > 0) {
            delay(1000)
            descansoRestante--
            if (descansoRestante == 10) {
                falar("Faltam 10 segundos de descanso.")
            } else if (descansoRestante == 0) {
                GerenciadorNotificacoes.notificarFimDescanso(contexto, nomeExercicio)
                falar("Descanso finalizado! Pronto para a próxima série.")
            }
        }
    }

    val nomeArticulacao = remember(nomeExercicio, grupoMuscular) {
        padraoArticularParaExercicio(nomeExercicio, grupoMuscular)
    }

    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // =============================================================
                // 1. TOP BAR: Badge AO VIVO IA + Série + Botões (Voz, Inverter Câmera, Fechar)
                // =============================================================
                Surface(
                    color = FundoAcademia.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(VerdeEsmeralda),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = FundoAcademia)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VermelhoAlerta.copy(alpha = 0.22f)
                                    ) {
                                        Text(
                                            text = "● AO VIVO IA",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = VermelhoAlerta,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Série #$numeroSerie/$totalSeriesDoExercicio • ${pesoKgStr}kg",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VerdeEsmeralda
                                    )
                                }
                                Text(
                                    text = nomeExercicio,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextoPrimario,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { vozAtiva = !vozAtiva },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (vozAtiva) VerdeEsmeralda.copy(alpha = 0.2f) else SuperficieCard)
                            ) {
                                Icon(
                                    imageVector = if (vozAtiva) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Voz do Coach IA",
                                    tint = if (vozAtiva) VerdeEsmeralda else TextoSecundario,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = { usarCameraFrontal = !usarCameraFrontal },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuperficieCard)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Alternar Câmera Frontal/Traseira",
                                    tint = TextoPrimario,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = aoFechar,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuperficieCard)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = TextoSecundario,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // 2. VIEWPORT CENTRAL DA CÂMERA AO VIVO + OVERLAY BIOMECÂNICO + HUDs
                // =============================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    if (temPermissaoCamera) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    previewViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlay Gráfico de Mira Biomecânica e Vetores Articulares
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val corGuia = if (faseMovimento == "down") AmbarDestaque else VerdeEsmeralda

                            // Pontos de referência biomecânica (Ombro -> Articulação -> Extremidade)
                            val pSuperior = Offset(cx - 75f, cy - 120f)
                            val pVertice = Offset(cx - 95f, cy + 10f)
                            val pInferior = if (faseMovimento == "down") {
                                Offset(cx - 20f, cy - 40f)
                            } else {
                                Offset(cx - 80f, cy + 135f)
                            }

                            drawLine(
                                color = corGuia.copy(alpha = 0.85f),
                                start = pSuperior,
                                end = pVertice,
                                strokeWidth = 10f
                            )
                            drawLine(
                                color = corGuia.copy(alpha = 0.85f),
                                start = pVertice,
                                end = pInferior,
                                strokeWidth = 10f
                            )

                            listOf(pSuperior, pVertice, pInferior).forEach { pt ->
                                drawCircle(color = Color.White, radius = 13f, center = pt)
                                drawCircle(color = corGuia, radius = 8f, center = pt)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = VermelhoAlerta,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Permissão de Câmera Necessária",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Libere o acesso à câmera para o Coach IA acompanhar sua postura ao vivo.",
                                fontSize = 12.sp,
                                color = TextoSecundario,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { permissaoCameraLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda)
                            ) {
                                Text("Permitir Câmera Agora", fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // HUD Superior Esquerdo: Ângulo Articular + Fase + Modo Auto/Manual
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FundoAcademia.copy(alpha = 0.86f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = nomeArticulacao.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = TextoSecundario
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${anguloArticular}°",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (faseMovimento == "down") AmbarDestaque.copy(alpha = 0.22f) else VerdeEsmeralda.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = if (faseMovimento == "down") "Contração / Descida" else "Estendido / Topo",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (faseMovimento == "down") AmbarDestaque else VerdeEsmeralda,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "🎙️ Cadência: $rotuloFaseVoz",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CianoNeon
                            )
                        }
                    }

                    // HUD Superior Direito: Contador Gigante de Repetições + Botões [-] / [+]
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = FundoAcademia.copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "REPETIÇÕES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = VerdeEsmeralda
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$repAtual",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "/$alvoReps",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextoSecundario,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SuperficieCard)
                                        .clickable { repAtual = (repAtual - 1).coerceAtLeast(0) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("−", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VerdeEsmeralda)
                                        .clickable {
                                            repAtual++
                                            falar("$repAtual")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", fontWeight = FontWeight.Black, color = FundoAcademia, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    // Banner Inferior da Câmera: Resposta de Voz do Coach IA ou Análise de Postura Gemini Vision
                    if ((feedbackVisualIa != null || ultimaRespostaVozIa != null) && modoAtual == "executing") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = FundoAcademia.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (ultimaRespostaVozIa != null) CianoNeon else VerdeEsmeralda,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    if (ultimaRespostaVozIa != null) {
                                        Text(
                                            text = "🎙️ RESPOSTA EM ÁUDIO DO COACH IA" +
                                                (if (!ultimaPerguntaVoz.isNullOrBlank()) " (“$ultimaPerguntaVoz”)" else ""),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = CianoNeon
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "“${ultimaRespostaVozIa!!}”",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    } else if (feedbackVisualIa != null) {
                                        val fb = feedbackVisualIa!!
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "ANÁLISE DE POSTURA GEMINI VISION",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = VerdeEsmeralda
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = VerdeEsmeralda.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = fb.postureStatus.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = VerdeEsmeraldaClaro,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "“${fb.shortVoiceTip}”",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = fb.biomechanicalFeedback,
                                            fontSize = 11.sp,
                                            color = TextoSecundario
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================
                    // OVERLAY DE DESCANSO IN-LIVE (Quando a Série é Concluída!)
                    // =========================================================
                    if (modoAtual == "resting") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(FundoAcademia.copy(alpha = 0.90f))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = SuperficieCard,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = VerdeEsmeralda.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "✅ Série #$numeroSerie Salva (${if (repAtual > 0) repAtual else alvoReps} reps)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = VerdeEsmeralda,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    Text(
                                        text = "CRONÔMETRO DE DESCANSO IN-LIVE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextoSecundario
                                    )

                                    Text(
                                        text = "${descansoRestante}s",
                                        fontSize = 68.sp,
                                        fontWeight = FontWeight.Black,
                                        color = AmbarDestaque
                                    )

                                    LinearProgressIndicator(
                                        progress = {
                                            (descansoRestante.toFloat() / descansoSegundos.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = AmbarDestaque,
                                        trackColor = FundoAcademia
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { descansoRestante = (descansoRestante - 15).coerceAtLeast(0) }
                                        ) {
                                            Text("−15s", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { descansoRestante += 15 }
                                        ) {
                                            Text("+15s", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = iniciarEscutaMicrofone
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Falar c/ IA", fontWeight = FontWeight.Bold, color = CianoNeon)
                                        }
                                    }

                                    if (proximaSerieDescricao != null && aoAvancarParaProximaSerie != null) {
                                        Text(
                                            text = "Próxima: $proximaSerieDescricao",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CianoNeon
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (aoAvancarParaProximaSerie != null) {
                                                    repAtual = 0
                                                    modoAtual = "executing"
                                                    aoAvancarParaProximaSerie()
                                                } else {
                                                    repAtual = 0
                                                    modoAtual = "executing"
                                                    falar("Iniciando próxima série! Vamos lá!")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1.3f).height(48.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Próxima Série", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = aoFechar,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Text("Voltar à Ficha", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 3. RODAPÉ DE CONTROLES AO VIVO: Controle de Mídia + Avaliar Postura IA +
                //    Conversar por Voz com a IA (Microfone) + Concluir Série
                // =============================================================
                Surface(
                    color = FundoAcademia,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Controle de Mídia Nativo (Spotify / YouTube Music) direto na Câmera Live!
                        BarraControleMidiaTelaCheia()

                        // Linha de Ações de IA (Zerar | Avaliar Postura Vision | 🎙️ Falar c/ IA em Áudio)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    repAtual = 0
                                    falar("Contador zerado")
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zerar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                enabled = !analisandoFrameIa,
                                onClick = avaliarPosturaComGeminiVision,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (analisandoFrameIa) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("IA Analisando...", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = VerdeEsmeralda, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Avaliar Postura", fontSize = 11.sp, fontWeight = FontWeight.Black, color = VerdeEsmeralda)
                                }
                            }

                            Button(
                                onClick = iniciarEscutaMicrofone,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = FundoAcademia, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "🎙️ Falar c/ IA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FundoAcademia
                                )
                            }
                        }

                        // Botão Principal: Concluir Série & Iniciar Descanso Live
                        Button(
                            onClick = acionarDescansoInLive,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Concluir Série (${if (repAtual > 0) repAtual else alvoReps} reps) & Descanso Live (${descansoSegundos}s)",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
