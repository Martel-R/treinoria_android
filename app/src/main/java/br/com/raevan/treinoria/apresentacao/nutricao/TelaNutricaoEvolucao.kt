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
import br.com.raevan.treinoria.dados.remoto.dto.MedidaCorporalDto
import br.com.raevan.treinoria.dados.remoto.dto.RefeicaoLogDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.saude.GerenciadorHealthConnect
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private fun bitmapParaBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    val bytes = stream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

@Composable
fun TelaNutricaoEvolucao() {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var abaInterna by remember { mutableStateOf(0) } // 0 = Nutrição IA, 1 = Medidas & Evolução

    // Inicializa instantaneamente a partir do Cache em Memória!
    var refeicoes by remember {
        mutableStateOf<List<RefeicaoLogDto>>(RepositorioTreinoria.cacheRefeicoes)
    }
    var medidas by remember {
        mutableStateOf<List<MedidaCorporalDto>>(RepositorioTreinoria.cacheMedidas)
    }
    var carregando by remember {
        mutableStateOf(
            RepositorioTreinoria.cacheRefeicoes.isEmpty() && RepositorioTreinoria.cacheMedidas.isEmpty()
        )
    }

    var tipoRefeicao by remember { mutableStateOf("Pós-Treino") }
    var descricaoPrato by remember { mutableStateOf("") }
    var fotoCapturadaBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analisandoPrato by remember { mutableStateOf(false) }
    var mensagemScanner by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            fotoCapturadaBitmap = bmp
            mensagemScanner = "📸 Foto capturada! Toque em 'Escanear com IA & Salvar' abaixo."
        }
    }

    val galeriaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contexto.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        fotoCapturadaBitmap = bmp
                        mensagemScanner = "🖼️ Imagem selecionada! Toque em 'Escanear com IA & Salvar' abaixo."
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var pesoKg by remember { mutableStateOf("") }
    var bfPct by remember { mutableStateOf("") }
    var bracoCm by remember { mutableStateOf("") }
    var cinturaCm by remember { mutableStateOf("") }
    var peitoCm by remember { mutableStateOf("") }
    var coxaCm by remember { mutableStateOf("") }
    var salvandoMedida by remember { mutableStateOf(false) }

    val recarregar = { forcar: Boolean ->
        escopo.launch {
            if (refeicoes.isEmpty() && medidas.isEmpty()) carregando = true
            refeicoes = RepositorioTreinoria.listarRefeicoesRecentes(forcarAtualizacao = forcar)
            medidas = RepositorioTreinoria.listarMedidasCorporais(forcarAtualizacao = forcar)
            val resumoSaude = GerenciadorHealthConnect.lerResumoDiario(contexto)
            if (pesoKg.isBlank() && resumoSaude.ultimoPesoKg != null && resumoSaude.ultimoPesoKg > 0) {
                pesoKg = resumoSaude.ultimoPesoKg.toString()
            }
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        recarregar(false)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = abaInterna == 0,
                    onClick = { abaInterna = 0 },
                    label = { Text("📸 Scanner Nutricional IA", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = abaInterna == 1,
                    onClick = { abaInterna = 1 },
                    label = { Text("📏 Medidas & Evolução", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (abaInterna == 0) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scanner de Prato por Câmera ou Texto (IA)", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "Tire uma foto do seu prato, escolha da galeria ou descreva o que comeu para calcular os macros com Gemini 2.5 Vision:",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { cameraLauncher.launch() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = VerdeEsmeralda)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tirar Foto", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { galeriaLauncher.launch("image/*") },
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
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    bitmap = fotoCapturadaBitmap!!.asImageBitmap(),
                                    contentDescription = "Foto do prato",
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
                                        "Observação opcional (ex: 200g arroz + frango)"
                                    else
                                        "Ou descreva o prato (ex: 180g frango, 200g arroz, 2 ovos)"
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
                                        fotoCapturadaBitmap = null
                                        descricaoPrato = ""
                                        mensagemScanner = "✅ Foto analisada e salva: ${resultado.dishName} (${resultado.caloriesKcal} kcal)!"
                                    } else {
                                        val respIa = RepositorioTreinoria.consultarCoachIa(
                                            "Estime macronutrientes para: '$descricaoPrato'. Retorne SOMENTE um JSON válido no formato: " +
                                                "{\"calorias\":550,\"proteina\":42,\"carbo\":58,\"gordura\":12,\"feedback\":\"Ótima refeição pós-treino!\"}"
                                        )
                                        val limpo = respIa.substringAfter("{").substringBeforeLast("}")
                                        var cal = 520
                                        var prot = 40.0
                                        var carb = 55.0
                                        var gord = 14.0
                                        var fb = "Excelente aporte proteico para hipertrofia!"
                                        try {
                                            val obj = JSONObject("{$limpo}")
                                            cal = obj.optInt("calorias", 520)
                                            prot = obj.optDouble("proteina", 40.0)
                                            carb = obj.optDouble("carbo", 55.0)
                                            gord = obj.optDouble("gordura", 14.0)
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
                                        descricaoPrato = ""
                                        mensagemScanner = "✅ Refeição calculada e salva ($cal kcal)!"
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
                                Text("Coach IA analisando prato...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (fotoCapturadaBitmap != null) "Escanear Foto com IA & Salvar" else "Analisar com IA & Salvar",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            items(refeicoes, key = { it.id }) { ref ->
                Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ref.dishName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        } else {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Registrar Medidas Corporais", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
                            Text("Salvar Medidas & Sincronizar Health Connect", fontWeight = FontWeight.Black)
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
                                "Peito: ${m.chestCm ?: "-"}cm • Cintura: ${m.waistCm ?: "-"}cm • Braço: ${m.armsCm ?: "-"}cm",
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
        }
    }
}
