package br.com.raevan.treinoria.apresentacao.nutricao

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.*
import br.com.raevan.treinoria.dados.repositorios.ComparacaoShapeIaResultado
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.notificacoes.GerenciadorNotificacoes
import br.com.raevan.treinoria.nucleo.saude.GerenciadorHealthConnect
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private fun bitmapParaBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    val bytes = stream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaNutricaoEvolucao(
    iniciarCameraPreTreino: Boolean = false,
    aoConsumirGatilhoCamera: () -> Unit = {}
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    // 3 Sub-Abas Completas: [0] 🍽️ Nutrição & Pré-Treino | [1] 📏 Corpo & Medidas | [2] 📸 Galeria & Antes/Depois IA
    var abaInterna by remember { mutableStateOf(0) }

    var perfil by remember { mutableStateOf<PerfilDto?>(RepositorioTreinoria.cachePerfil) }
    var sessoes by remember { mutableStateOf<List<SessaoTreinoDto>>(RepositorioTreinoria.cacheSessoes) }
    var refeicoes by remember {
        mutableStateOf<List<RefeicaoLogDto>>(RepositorioTreinoria.cacheRefeicoes)
    }
    var medidas by remember {
        mutableStateOf<List<MedidaCorporalDto>>(RepositorioTreinoria.cacheMedidas)
    }
    var fotosProgresso by remember {
        mutableStateOf<List<FotoProgressoDto>>(RepositorioTreinoria.cacheFotosProgresso)
    }
    val bitmapsGaleria = remember { mutableStateMapOf<String, Bitmap>() }

    // Cronograma Inteligente de Pré-Treino baseado no horário médio da última semana
    val cronogramaPreTreino = remember(sessoes) {
        GerenciadorNotificacoes.agendarLembreteDiarioPreTreino(contexto, sessoes)
    }

    // Campos do Scanner de Prato / Foto de Pré-Treino
    var tipoRefeicao by remember { mutableStateOf("Pré-Treino") }
    var descricaoPrato by remember { mutableStateOf("") }
    var fotoCapturadaBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analisandoPrato by remember { mutableStateOf(false) }
    var mensagemScanner by remember { mutableStateOf<String?>(null) }

    val cameraPratoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            fotoCapturadaBitmap = bmp
            mensagemScanner = "📸 Foto da refeição ($tipoRefeicao) capturada! Toque em 'Escanear Foto com IA & Salvar' abaixo."
        }
    }

    val galeriaPratoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contexto.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        fotoCapturadaBitmap = bmp
                        mensagemScanner = "🖼️ Imagem selecionada! Toque em 'Escanear Foto com IA & Salvar' abaixo."
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Se o usuário clicou na notificação "📸 Enviar Foto do Pré-Treino" ou no botão da Home,
    // seleciona "Pré-Treino" e abre a câmera automaticamente!
    LaunchedEffect(iniciarCameraPreTreino) {
        if (iniciarCameraPreTreino) {
            abaInterna = 0
            tipoRefeicao = "Pré-Treino"
            aoConsumirGatilhoCamera()
            try {
                cameraPratoLauncher.launch()
            } catch (_: Throwable) {}
        }
    }

    // Campos de Medidas Corporais
    var pesoKg by remember { mutableStateOf("") }
    var bfPct by remember { mutableStateOf("") }
    var bracoCm by remember { mutableStateOf("") }
    var cinturaCm by remember { mutableStateOf("") }
    var peitoCm by remember { mutableStateOf("") }
    var coxaCm by remember { mutableStateOf("") }
    var salvandoMedida by remember { mutableStateOf(false) }

    // Campos da Galeria do Shape & Comparador Antes x Depois IA
    var fotoShapeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var categoriaFotoShape by remember { mutableStateOf("Evolução") }
    var legendaFotoShape by remember { mutableStateOf("") }
    var enviandoFotoShape by remember { mutableStateOf(false) }
    var idxFotoAntes by remember { mutableStateOf(0) }
    var idxFotoDepois by remember { mutableStateOf(0) }
    var analisandoAntesDepoisIa by remember { mutableStateOf(false) }
    var resultadoComparacaoIa by remember { mutableStateOf<ComparacaoShapeIaResultado?>(null) }

    val cameraShapeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) fotoShapeBitmap = bmp
    }

    val galeriaShapeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contexto.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) fotoShapeBitmap = bmp
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        perfil = RepositorioTreinoria.carregarPerfil(forcarAtualizacao = false)
        sessoes = RepositorioTreinoria.listarSessoesRecentes(15, forcarAtualizacao = false)
        refeicoes = RepositorioTreinoria.listarRefeicoesRecentes(forcarAtualizacao = false)
        medidas = RepositorioTreinoria.listarMedidasCorporais(forcarAtualizacao = false)
        fotosProgresso = RepositorioTreinoria.listarFotosProgresso(forcarAtualizacao = false)
        if (fotosProgresso.size >= 2) {
            idxFotoAntes = fotosProgresso.lastIndex
            idxFotoDepois = 0
        }
        val resumoSaude = GerenciadorHealthConnect.lerResumoDiario(contexto)
        if (pesoKg.isBlank() && resumoSaude.ultimoPesoKg != null && resumoSaude.ultimoPesoKg > 0) {
            pesoKg = resumoSaude.ultimoPesoKg.toString()
        }
    }

    LaunchedEffect(fotosProgresso) {
        fotosProgresso.take(12).forEach { f ->
            if (!bitmapsGaleria.containsKey(f.photoUrl)) {
                val b = RepositorioTreinoria.baixarBitmapPorUrl(f.photoUrl)
                if (b != null) bitmapsGaleria[f.photoUrl] = b
            }
        }
    }

    // Cálculo de Metas Diárias de Macronutrientes baseadas no Peso e Objetivo do Atleta!
    val pesoBaseCalculo = remember(perfil, medidas) {
        medidas.firstOrNull()?.weightKg ?: perfil?.weightKg ?: 78.0
    }
    val metaProteinaDia = remember(pesoBaseCalculo) { (pesoBaseCalculo * 2.1).roundToInt() }
    val metaCarboDia = remember(pesoBaseCalculo, perfil?.goal) {
        val mult = if ((perfil?.goal ?: "").contains("Cutting", ignoreCase = true)) 2.4 else 3.6
        (pesoBaseCalculo * mult).roundToInt()
    }
    val metaGorduraDia = remember(pesoBaseCalculo) { (pesoBaseCalculo * 0.9).roundToInt() }
    val metaCaloriasDia = remember(metaProteinaDia, metaCarboDia, metaGorduraDia) {
        (metaProteinaDia * 4) + (metaCarboDia * 4) + (metaGorduraDia * 9)
    }

    val dataHojeIso = remember { RepositorioTreinoria.obterDataHojeIso() }
    val refeicoesDeHoje = remember(refeicoes, dataHojeIso) {
        refeicoes.filter { it.loggedAt.take(10) == dataHojeIso }.ifEmpty { refeicoes.take(3) }
    }
    val caloriasHoje = remember(refeicoesDeHoje) { refeicoesDeHoje.sumOf { it.caloriesKcal } }
    val proteinaHoje = remember(refeicoesDeHoje) { refeicoesDeHoje.sumOf { it.proteinG }.roundToInt() }
    val carboHoje = remember(refeicoesDeHoje) { refeicoesDeHoje.sumOf { it.carbsG }.roundToInt() }
    val gorduraHoje = remember(refeicoesDeHoje) { refeicoesDeHoje.sumOf { it.fatG }.roundToInt() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // =====================================================================
        // SELETOR SUPERIOR: [🍽️ Nutrição & Pré-Treino] | [📏 Corpo & Medidas] | [📸 Shape & IA]
        // =====================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = abaInterna == 0,
                    onClick = { abaInterna = 0 },
                    label = {
                        Text(
                            "🍽️ Nutri & Pré-Treino",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(1.15f)
                )
                FilterChip(
                    selected = abaInterna == 1,
                    onClick = { abaInterna = 1 },
                    label = {
                        Text(
                            "📏 Corpo (${medidas.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(0.95f)
                )
                FilterChip(
                    selected = abaInterna == 2,
                    onClick = { abaInterna = 2 },
                    label = {
                        Text(
                            "📸 Shape (${fotosProgresso.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(0.95f)
                )
            }
        }

        // =====================================================================
        // ABA 0: NUTRIÇÃO INTELIGENTE + LEMBRETE DE PRÉ-TREINO + SCANNER DE FOTO
        // =====================================================================
        if (abaInterna == 0) {
            // 1. Painel de Metas Diárias de Macronutrientes (Kcal, Proteína, Carbo, Gordura)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "🔥 Painel Diário de Macronutrientes",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = VerdeEsmeralda
                                )
                                Text(
                                    text = "Calculado para ${pesoBaseCalculo.toInt()}kg • Objetivo: ${perfil?.goal ?: "Hipertrofia"}",
                                    fontSize = 11.sp,
                                    color = TextoSecundario
                                )
                            }
                            Text(
                                text = "$caloriasHoje / $metaCaloriasDia kcal",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = AmbarDestaque
                            )
                        }

                        LinearProgressIndicator(
                            progress = {
                                (caloriasHoje.toFloat() / metaCaloriasDia.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AmbarDestaque,
                            trackColor = FundoAcademia
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("🥩 Proteína", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        "${proteinaHoje}g / ${metaProteinaDia}g",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = VerdeEsmeralda
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("🍚 Carboidrato", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        "${carboHoje}g / ${metaCarboDia}g",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = CianoNeon
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("🥑 Gordura", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        "${gorduraHoje}g / ${metaGorduraDia}g",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = AmbarDestaque
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Card Inteligente: Lembrete de Horário de Pré-Treino + Opções Acessíveis + Solicitação de Foto!
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⏰ Alerta de Pré-Treino: Comer às ${cronogramaPreTreino.horarioLembreteComerFormatado}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = AmbarDestaque
                                )
                                Text(
                                    text = "Calculado pelo seu horário médio de treino na última semana (${cronogramaPreTreino.horarioMedioTreinoFormatado})",
                                    fontSize = 11.sp,
                                    color = TextoSecundario
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    GerenciadorNotificacoes.dispararNotificacaoRefeicaoPreTreino(
                                        context = contexto,
                                        horarioMedioTreino = cronogramaPreTreino.horarioMedioTreinoFormatado,
                                        horarioRefeicao = cronogramaPreTreino.horarioLembreteComerFormatado
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Notificar 🔔", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CianoNeon)
                            }
                        }

                        Text(
                            text = "💰 Opções Acessíveis de Pré-Treino (Toque para usar ou tire foto do seu prato):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = VerdeEsmeraldaClaro
                        )

                        cronogramaPreTreino.opcoesAcessiveis.forEach { op ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tipoRefeicao = "Pré-Treino"
                                        descricaoPrato = "${op.titulo} (${op.ingredientes})"
                                        mensagemScanner = "⚡ Opção selecionada! Agora tire uma foto da sua refeição de Pré-Treino para validar com a IA!"
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${op.titulo} (${op.custoMedio})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TextoPrimario
                                        )
                                        Text(
                                            text = op.ingredientes,
                                            fontSize = 11.sp,
                                            color = TextoSecundario
                                        )
                                        Text(
                                            text = op.macrosResumo,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CianoNeon
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Fotografar Pré-Treino",
                                        tint = VerdeEsmeralda,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                tipoRefeicao = "Pré-Treino"
                                cameraPratoLauncher.launch()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmbarDestaque),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = FundoAcademia)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📸 Fotografar Minha Refeição de Pré-Treino Agora",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = FundoAcademia
                            )
                        }
                    }
                }
            }

            // 3. Scanner de Refeição por Foto ou Texto (Gemini 2.5 Vision)
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "📸 Scanner Nutricional IA (Foto do Prato ou Texto)",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )

                        // Seletor do Tipo de Refeição
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Pré-Treino", "Pós-Treino", "Café da Manhã", "Almoço", "Jantar").forEach { tipo ->
                                FilterChip(
                                    selected = tipoRefeicao == tipo,
                                    onClick = { tipoRefeicao = tipo },
                                    label = { Text(tipo, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { cameraPratoLauncher.launch() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = VerdeEsmeralda)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tirar Foto ($tipoRefeicao)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { galeriaPratoLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = CianoNeon)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeria", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (fotoCapturadaBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    bitmap = fotoCapturadaBitmap!!.asImageBitmap(),
                                    contentDescription = "Foto da refeição",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { fotoCapturadaBitmap = null },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remover foto", tint = VermelhoAlerta)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = descricaoPrato,
                            onValueChange = { descricaoPrato = it },
                            label = {
                                Text(
                                    if (fotoCapturadaBitmap != null)
                                        "Observação opcional (ex: 2 bananas + aveia)"
                                    else
                                        "Ou descreva a refeição ($tipoRefeicao)"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (mensagemScanner != null) {
                            Text(
                                text = mensagemScanner!!,
                                fontSize = 12.sp,
                                color = VerdeEsmeraldaClaro,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            enabled = !analisandoPrato && (fotoCapturadaBitmap != null || descricaoPrato.isNotBlank()),
                            onClick = {
                                analisandoPrato = true
                                mensagemScanner = null
                                escopo.launch {
                                    if (fotoCapturadaBitmap != null) {
                                        val b64 = bitmapParaBase64(fotoCapturadaBitmap!!)
                                        val resultado = RepositorioTreinoria.analisarFotoPratoComIa(
                                            imagemBase64 = b64,
                                            tipoRefeicao = tipoRefeicao,
                                            descricaoExtra = descricaoPrato
                                        )
                                        RepositorioTreinoria.registrarRefeicao(
                                            tipoRefeicao = tipoRefeicao,
                                            titulo = resultado.dishName,
                                            calorias = resultado.caloriesKcal,
                                            proteinaG = resultado.proteinG,
                                            carboG = resultado.carbsG,
                                            gorduraG = resultado.fatG,
                                            feedbackIa = resultado.aiFeedback,
                                            adherenceScore = resultado.adherenceScore
                                        )
                                        // Se for Pré-Treino, já marca automaticamente o hábito diário preWorkoutTaken = true!
                                        if (tipoRefeicao == "Pré-Treino") {
                                            val hab = RepositorioTreinoria.obterHabitoHoje()
                                            RepositorioTreinoria.atualizarHabitoHoje(
                                                waterMl = hab.waterMl,
                                                creatineTaken = hab.creatineTaken,
                                                preWorkoutTaken = true,
                                                postWorkoutTaken = hab.postWorkoutTaken,
                                                waterGoalMl = hab.waterGoalMl
                                            )
                                        }
                                        fotoCapturadaBitmap = null
                                        descricaoPrato = ""
                                        mensagemScanner = "✅ Foto de $tipoRefeicao analisada e salva: ${resultado.dishName} (${resultado.caloriesKcal} kcal)!"
                                    } else {
                                        val respIa = RepositorioTreinoria.consultarCoachIa(
                                            "Estime macronutrientes para ($tipoRefeicao): '$descricaoPrato'. Retorne SOMENTE um JSON válido no formato: " +
                                                "{\"calorias\":420,\"proteina\":28,\"carbo\":52,\"gordura\":10,\"feedback\":\"Ótima energia para o treino!\"}"
                                        )
                                        val limpo = respIa.substringAfter("{").substringBeforeLast("}")
                                        var cal = 420
                                        var prot = 28.0
                                        var carb = 52.0
                                        var gord = 10.0
                                        var fb = "Excelente combinação de nutrientes para o seu objetivo!"
                                        try {
                                            val obj = JSONObject("{$limpo}")
                                            cal = obj.optInt("calorias", 420)
                                            prot = obj.optDouble("proteina", 28.0)
                                            carb = obj.optDouble("carbo", 52.0)
                                            gord = obj.optDouble("gordura", 10.0)
                                            fb = obj.optString("feedback", fb)
                                        } catch (_: Exception) {}

                                        RepositorioTreinoria.registrarRefeicao(
                                            tipoRefeicao = tipoRefeicao,
                                            titulo = descricaoPrato,
                                            calorias = cal,
                                            proteinaG = prot,
                                            carboG = carb,
                                            gorduraG = gord,
                                            feedbackIa = fb
                                        )
                                        if (tipoRefeicao == "Pré-Treino") {
                                            val hab = RepositorioTreinoria.obterHabitoHoje()
                                            RepositorioTreinoria.atualizarHabitoHoje(
                                                waterMl = hab.waterMl,
                                                creatineTaken = hab.creatineTaken,
                                                preWorkoutTaken = true,
                                                postWorkoutTaken = hab.postWorkoutTaken,
                                                waterGoalMl = hab.waterGoalMl
                                            )
                                        }
                                        descricaoPrato = ""
                                        mensagemScanner = "✅ $tipoRefeicao calculado e salvo ($cal kcal)!"
                                    }
                                    analisandoPrato = false
                                    refeicoes = RepositorioTreinoria.cacheRefeicoes
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (analisandoPrato) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nutricionista IA analisando prato...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (fotoCapturadaBitmap != null) "Escanear Foto ($tipoRefeicao) c/ IA & Salvar" else "Analisar ($tipoRefeicao) c/ IA & Salvar",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // Histórico de Refeições Registradas
            items(refeicoes, key = { it.id }) { ref ->
                Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (ref.mealType.contains("Pré", ignoreCase = true)) AmbarDestaque.copy(alpha = 0.2f) else VerdeEsmeralda.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = ref.mealType.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (ref.mealType.contains("Pré", ignoreCase = true)) AmbarDestaque else VerdeEsmeralda,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(ref.dishName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = {
                                    escopo.launch {
                                        RepositorioTreinoria.excluirRefeicao(ref.id)
                                        refeicoes = RepositorioTreinoria.cacheRefeicoes
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = TextoSecundario, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🔥 ${ref.caloriesKcal} kcal", color = AmbarDestaque, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("🥩 ${ref.proteinG.toInt()}g Prot", color = VerdeEsmeralda, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("🍚 ${ref.carbsG.toInt()}g Carb", color = CianoNeon, fontSize = 12.sp)
                            Text("🥑 ${ref.fatG.toInt()}g Gord", color = TextoSecundario, fontSize = 12.sp)
                        }
                        if (!ref.aiFeedback.isNullOrBlank()) {
                            Text("🤖 ${ref.aiFeedback}", fontSize = 11.sp, color = TextoSecundario)
                        }
                    }
                }
            }
        } else if (abaInterna == 1) {
            // =================================================================
            // ABA 1: CORPO, COMPOSIÇÃO CORPORAL (IMC, MASSA MAGRA, BF%) & MEDIDAS
            // =================================================================
            item {
                val ultima = medidas.firstOrNull()
                val penultima = medidas.getOrNull(1)
                val pesoAtualVal = ultima?.weightKg ?: perfil?.weightKg ?: 78.0
                val alturaM = ((perfil?.heightCm ?: 175.0) / 100.0).coerceAtLeast(1.2)
                val imc = pesoAtualVal / (alturaM * alturaM)
                val bfAtual = ultima?.bodyFatPct ?: 15.0
                val massaGordaKg = pesoAtualVal * (bfAtual / 100.0)
                val massaMagraKg = pesoAtualVal - massaGordaKg

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🧬 Raio-X de Composição Corporal",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = CianoNeon
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Massa Magra", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        String.format("%.1f kg", massaMagraKg),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = VerdeEsmeralda
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Gordura (${bfAtual}%)", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        String.format("%.1f kg", massaGordaKg),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = AmbarDestaque
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("IMC Atual", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        String.format("%.1f", imc),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = CianoNeon
                                    )
                                }
                            }
                        }

                        if (ultima != null && penultima != null) {
                            val dPeso = (ultima.weightKg ?: 0.0) - (penultima.weightKg ?: 0.0)
                            val dBraco = (ultima.armsCm ?: 0.0) - (penultima.armsCm ?: 0.0)
                            val dCintura = (ultima.waistCm ?: 0.0) - (penultima.waistCm ?: 0.0)
                            Text(
                                text = "📈 Variação desde ${penultima.measuredAt}: " +
                                    "Peso ${if (dPeso >= 0) "+" else ""}${String.format("%.1f", dPeso)}kg • " +
                                    "Braço ${if (dBraco >= 0) "+" else ""}${String.format("%.1f", dBraco)}cm • " +
                                    "Cintura ${if (dCintura >= 0) "+" else ""}${String.format("%.1f", dCintura)}cm",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerdeEsmeraldaClaro
                            )
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📏 Registrar Nova Avaliação Corporal", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pesoKg,
                                onValueChange = { pesoKg = it },
                                label = { Text("Peso (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = bfPct,
                                onValueChange = { bfPct = it },
                                label = { Text("BF (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = peitoCm,
                                onValueChange = { peitoCm = it },
                                label = { Text("Peitoral (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cinturaCm,
                                onValueChange = { cinturaCm = it },
                                label = { Text("Cintura (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = bracoCm,
                                onValueChange = { bracoCm = it },
                                label = { Text("Braço (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = coxaCm,
                                onValueChange = { coxaCm = it },
                                label = { Text("Coxa (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            enabled = !salvandoMedida && pesoKg.isNotBlank(),
                            onClick = {
                                salvandoMedida = true
                                escopo.launch {
                                    val pVal = pesoKg.replace(",", ".").toDoubleOrNull()
                                    RepositorioTreinoria.salvarMedidaCorporal(
                                        pesoKg = pVal,
                                        bfPct = bfPct.replace(",", ".").toDoubleOrNull(),
                                        peitoCm = peitoCm.replace(",", ".").toDoubleOrNull(),
                                        cinturaCm = cinturaCm.replace(",", ".").toDoubleOrNull(),
                                        bracoCm = bracoCm.replace(",", ".").toDoubleOrNull(),
                                        coxaCm = coxaCm.replace(",", ".").toDoubleOrNull(),
                                        notas = "Registrado via App Android"
                                    )
                                    if (pVal != null && pVal > 0) {
                                        GerenciadorHealthConnect.gravarPesoCorporal(contexto, pVal)
                                    }
                                    pesoKg = ""
                                    bfPct = ""
                                    salvandoMedida = false
                                    medidas = RepositorioTreinoria.cacheMedidas
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                "Salvar Medidas & Sincronizar Health Connect",
                                fontWeight = FontWeight.Black,
                                color = FundoAcademia
                            )
                        }
                    }
                }
            }

            items(medidas, key = { it.id }) { m ->
                Surface(shape = RoundedCornerShape(14.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("📅 ${m.measuredAt}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Peito: ${m.chestCm ?: "-"}cm • Cintura: ${m.waistCm ?: "-"}cm • Braço: ${m.armsCm ?: "-"}cm • Coxa: ${m.legsCm ?: "-"}cm",
                                fontSize = 11.sp,
                                color = TextoSecundario
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${m.weightKg ?: "-"} kg", fontWeight = FontWeight.Black, color = VerdeEsmeralda, fontSize = 15.sp)
                            if (m.bodyFatPct != null) {
                                Text("${m.bodyFatPct}% BF", fontSize = 11.sp, color = CianoNeon, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // =================================================================
            // ABA 2: GALERIA DE FOTOS DO SHAPE & COMPARADOR ANTES × DEPOIS COM IA (Igual à Web!)
            // =================================================================
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "📸 Adicionar Foto na Galeria de Evolução do Shape",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = VerdeEsmeralda
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Evolução", "Frente / Duplo Bíceps", "Costas / Dorsal", "Pós-Treino").forEach { cat ->
                                FilterChip(
                                    selected = categoriaFotoShape == cat,
                                    onClick = { categoriaFotoShape = cat },
                                    label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { cameraShapeLauncher.launch() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = VerdeEsmeralda)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Câmera", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { galeriaShapeLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = CianoNeon)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeria", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (fotoShapeBitmap != null) {
                            Image(
                                bitmap = fotoShapeBitmap!!.asImageBitmap(),
                                contentDescription = "Nova foto do shape",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }

                        OutlinedTextField(
                            value = legendaFotoShape,
                            onValueChange = { legendaFotoShape = it },
                            label = { Text("Legenda opcional (ex: Final do Bulking / 82kg)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            enabled = !enviandoFotoShape && fotoShapeBitmap != null,
                            onClick = {
                                enviandoFotoShape = true
                                escopo.launch {
                                    RepositorioTreinoria.enviarESalvarFotoProgresso(
                                        bitmap = fotoShapeBitmap!!,
                                        categoria = categoriaFotoShape,
                                        pesoKg = pesoBaseCalculo,
                                        legenda = legendaFotoShape
                                    )
                                    fotoShapeBitmap = null
                                    legendaFotoShape = ""
                                    fotosProgresso = RepositorioTreinoria.cacheFotosProgresso
                                    enviandoFotoShape = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                if (enviandoFotoShape) "Enviando p/ Galeria..." else "Salvar Foto na Galeria do Shape",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Comparador Visual Antes × Depois com IA Gemini Vision
            if (fotosProgresso.size >= 2) {
                item {
                    val fAntes = fotosProgresso.getOrNull(idxFotoAntes.coerceIn(0, fotosProgresso.lastIndex)) ?: fotosProgresso.last()
                    val fDepois = fotosProgresso.getOrNull(idxFotoDepois.coerceIn(0, fotosProgresso.lastIndex)) ?: fotosProgresso.first()
                    val bmpAntes = bitmapsGaleria[fAntes.photoUrl]
                    val bmpDepois = bitmapsGaleria[fDepois.photoUrl]

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SuperficieCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "⚖️ Comparador Antes × Depois (Gemini Vision IA)",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = CianoNeon
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("ANTES (${fAntes.takenAt})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextoSecundario)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(155.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(FundoAcademia)
                                            .clickable {
                                                idxFotoAntes = (idxFotoAntes + 1) % fotosProgresso.size
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bmpAntes != null) {
                                            Image(
                                                bitmap = bmpAntes.asImageBitmap(),
                                                contentDescription = "Antes",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text("Carregando...", fontSize = 11.sp, color = TextoSecundario)
                                        }
                                    }
                                    Text("Toque p/ trocar foto", fontSize = 9.sp, color = CianoNeon)
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("DEPOIS (${fDepois.takenAt})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerdeEsmeralda)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(155.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(FundoAcademia)
                                            .clickable {
                                                idxFotoDepois = (idxFotoDepois + 1) % fotosProgresso.size
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bmpDepois != null) {
                                            Image(
                                                bitmap = bmpDepois.asImageBitmap(),
                                                contentDescription = "Depois",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text("Carregando...", fontSize = 11.sp, color = TextoSecundario)
                                        }
                                    }
                                    Text("Toque p/ trocar foto", fontSize = 9.sp, color = CianoNeon)
                                }
                            }

                            Button(
                                enabled = !analisandoAntesDepoisIa,
                                onClick = {
                                    analisandoAntesDepoisIa = true
                                    escopo.launch {
                                        resultadoComparacaoIa = RepositorioTreinoria.compararFotosAntesDepoisComIa(
                                            context = contexto,
                                            fotoAntes = fAntes,
                                            fotoDepois = fDepois
                                        )
                                        analisandoAntesDepoisIa = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                if (analisandoAntesDepoisIa) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("IA comparando evolução estética...", fontSize = 12.sp, fontWeight = FontWeight.Black, color = FundoAcademia)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FundoAcademia)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Comparar Evolução Antes × Depois com IA", fontSize = 12.sp, fontWeight = FontWeight.Black, color = FundoAcademia)
                                }
                            }

                            if (resultadoComparacaoIa != null) {
                                val res = resultadoComparacaoIa!!
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = FundoAcademia,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(res.headline, fontWeight = FontWeight.Black, fontSize = 13.sp, color = VerdeEsmeralda)
                                        res.visualChanges.forEach { ch ->
                                            Text("• $ch", fontSize = 11.sp, color = TextoPrimario)
                                        }
                                        Text("💪 Destaques: ${res.muscleHighlights}", fontSize = 11.sp, color = CianoNeon, fontWeight = FontWeight.Bold)
                                        Text("🎯 Próximo Foco: ${res.nextFocusTip}", fontSize = 11.sp, color = TextoSecundario)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lista da Galeria de Fotos do Shape
            items(fotosProgresso, key = { it.id }) { foto ->
                val bmp = bitmapsGaleria[foto.photoUrl]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = foto.caption ?: foto.category,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${foto.category} • ${foto.takenAt}" + (if (foto.weightKg != null) " (${foto.weightKg}kg)" else ""),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = VerdeEsmeralda
                                )
                                if (!foto.caption.isNullOrBlank()) {
                                    Text(foto.caption, fontSize = 12.sp, color = TextoPrimario)
                                }
                            }
                            IconButton(
                                onClick = {
                                    escopo.launch {
                                        RepositorioTreinoria.excluirFotoProgresso(foto.id)
                                        fotosProgresso = RepositorioTreinoria.cacheFotosProgresso
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = VermelhoAlerta)
                            }
                        }
                    }
                }
            }
        }
    }
}
