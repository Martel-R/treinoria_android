package br.com.raevan.treinoria.apresentacao.nutricao

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    var refeicoes by remember { mutableStateOf<List<RefeicaoLogDto>>(emptyList()) }
    var medidas by remember { mutableStateOf<List<MedidaCorporalDto>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Campos Nutrição IA (Foto + Texto)
    var tipoRefeicao by remember { mutableStateOf("Pós-Treino") }
    var descricaoPrato by remember { mutableStateOf("") }
    var fotoCapturadaBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analisandoPrato by remember { mutableStateOf(false) }
    var mensagemScanner by remember { mutableStateOf<String?>(null) }

    // Launcher para Tirar Foto com a Câmera Nativa
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            fotoCapturadaBitmap = bmp
            mensagemScanner = "📸 Foto capturada! Toque em 'Escanear com IA & Salvar' abaixo."
        }
    }

    // Launcher para Escolher Foto da Galeria
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

    // Campos Medidas Corporais
    var pesoKg by remember { mutableStateOf("") }
    var bfPct by remember { mutableStateOf("") }
    var bracoCm by remember { mutableStateOf("") }
    var cinturaCm by remember { mutableStateOf("") }
    var peitoCm by remember { mutableStateOf("") }
    var coxaCm by remember { mutableStateOf("") }
    var salvandoMedida by remember { mutableStateOf(false) }

    val recarregar = {
        escopo.launch {
            carregando = true
            refeicoes = RepositorioTreinoria.listarRefeicoesRecentes()
            medidas = RepositorioTreinoria.listarMedidasCorporais()
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        recarregar()
    }

    val kcalHoje = remember(refeicoes) { refeicoes.take(5).sumOf { it.caloriesKcal } }
    val proteinaHoje = remember(refeicoes) { refeicoes.take(5).sumOf { it.proteinG }.toInt() }

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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Calorias Recentes", fontSize = 11.sp, color = TextoSecundario)
                            Text("$kcalHoje kcal", fontSize = 20.sp, fontWeight = FontWeight.Black, color = AmbarDestaque)
                        }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Proteína Acumulada", fontSize = 11.sp, color = TextoSecundario)
                            Text("${proteinaHoje}g", fontSize = 20.sp, fontWeight = FontWeight.Black, color = VerdeEsmeralda)
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Scanner IA de Prato, Suplemento & Rótulo",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = VerdeEsmeralda
                        )
                        Text(
                            "Tire uma foto do seu prato ou suplemento (ou descreva abaixo) para o Coach IA calcular Calorias, Proteínas, Carboidratos e Gorduras:",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )

                        // Seletor do momento da refeição
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Pré-Treino", "Pós-Treino", "Refeição").forEach { tipo ->
                                FilterChip(
                                    selected = tipoRefeicao == tipo,
                                    onClick = { tipoRefeicao = tipo },
                                    label = { Text(tipo, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        // Botões de Tirar Foto na Hora ou Abrir Galeria
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { cameraLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tirar Foto", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { galeriaLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = VerdeEsmeralda, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Da Galeria", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VerdeEsmeralda)
                            }
                        }

                        // Pré-visualização da Foto Capturada
                        if (fotoCapturadaBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(FundoAcademia)
                            ) {
                                Image(
                                    bitmap = fotoCapturadaBitmap!!.asImageBitmap(),
                                    contentDescription = "Foto do prato",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { fotoCapturadaBitmap = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(FundoAcademia.copy(alpha = 0.75f), RoundedCornerShape(50))
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
                                        "Observação opcional (ex: 200g arroz + 180g frango)"
                                    else
                                        "Ou descreva o que você comeu..."
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
                                mensagemScanner = "🤖 IA analisando alimentos e calculando macronutrientes..."
                                escopo.launch {
                                    if (fotoCapturadaBitmap != null) {
                                        val b64 = bitmapParaBase64(fotoCapturadaBitmap!!)
                                        val resultadoFoto = RepositorioTreinoria.analisarFotoPratoComIa(
                                            imagemBase64 = b64,
                                            tipoRefeicao = tipoRefeicao,
                                            descricaoExtra = descricaoPrato.trim()
                                        )
                                        RepositorioTreinoria.registrarRefeicao(
                                            tipoRefeicao = tipoRefeicao,
                                            titulo = resultadoFoto.dishName,
                                            calorias = resultadoFoto.caloriesKcal,
                                            proteinaG = resultadoFoto.proteinG,
                                            carboG = resultadoFoto.carbsG,
                                            gorduraG = resultadoFoto.fatG,
                                            feedbackIa = resultadoFoto.aiFeedback,
                                            adherenceScore = resultadoFoto.adherenceScore
                                        )
                                    } else {
                                        val resp = RepositorioTreinoria.consultarCoachIa(
                                            "Estime os macronutrientes desta refeição ($tipoRefeicao): '$descricaoPrato'. " +
                                                "Responda APENAS um JSON puro sem markdown no formato: " +
                                                "{\"calories\": 550, \"protein_g\": 42, \"carbs_g\": 55, \"fat_g\": 14, \"feedback\": \"Excelente aporte proteico pós-treino!\"}"
                                        )
                                        var kcal = 480
                                        var prot = 38.0
                                        var carb = 45.0
                                        var fat = 12.0
                                        var fb = resp
                                        try {
                                            val limpo = resp.substringAfter("{").substringBeforeLast("}")
                                            val obj = JSONObject("{$limpo}")
                                            kcal = obj.optInt("calories", 480)
                                            prot = obj.optDouble("protein_g", 38.0)
                                            carb = obj.optDouble("carbs_g", 45.0)
                                            fat = obj.optDouble("fat_g", 12.0)
                                            fb = obj.optString("feedback", resp)
                                        } catch (_: Exception) {}

                                        RepositorioTreinoria.registrarRefeicao(
                                            tipoRefeicao = tipoRefeicao,
                                            titulo = descricaoPrato.trim(),
                                            calorias = kcal,
                                            proteinaG = prot,
                                            carboG = carb,
                                            gorduraG = fat,
                                            feedbackIa = fb
                                        )
                                    }

                                    fotoCapturadaBitmap = null
                                    descricaoPrato = ""
                                    analisandoPrato = false
                                    mensagemScanner = "✅ Refeição analisada e registrada com sucesso!"
                                    recarregar()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (analisandoPrato) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analisando Prato com IA...", fontWeight = FontWeight.Black)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Escanear com IA & Salvar Refeição", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            items(refeicoes, key = { it.id }) { ref ->
                Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "[${ref.mealType}] ${ref.dishName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${ref.caloriesKcal} kcal", fontWeight = FontWeight.Black, color = AmbarDestaque, fontSize = 14.sp)
                        }
                        Text(
                            "🥩 Prot: ${ref.proteinG.toInt()}g • 🍚 Carb: ${ref.carbsG.toInt()}g • 🥑 Gord: ${ref.fatG.toInt()}g",
                            fontSize = 12.sp,
                            color = VerdeEsmeraldaClaro
                        )
                        if (!ref.aiFeedback.isNullOrBlank()) {
                            Text(ref.aiFeedback, fontSize = 11.sp, color = TextoSecundario)
                        }
                    }
                }
            }
        } else {
            // Aba 1: Medidas Corporais & Evolução
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Registrar Nova Avaliação Corporal",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = CianoNeon
                        )
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
                                value = bracoCm,
                                onValueChange = { bracoCm = it },
                                label = { Text("Braço (cm)") },
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
                                value = peitoCm,
                                onValueChange = { peitoCm = it },
                                label = { Text("Peitoral (cm)") },
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
                                    RepositorioTreinoria.salvarMedidaCorporal(
                                        pesoKg = pesoKg.toDoubleOrNull(),
                                        bfPct = bfPct.toDoubleOrNull(),
                                        peitoCm = peitoCm.toDoubleOrNull(),
                                        cinturaCm = cinturaCm.toDoubleOrNull(),
                                        bracoCm = bracoCm.toDoubleOrNull(),
                                        coxaCm = coxaCm.toDoubleOrNull(),
                                        notas = "Registrado pelo App Android"
                                    )
                                    pesoKg = ""
                                    bfPct = ""
                                    salvandoMedida = false
                                    recarregar()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Salvar Medidas no Histórico", fontWeight = FontWeight.Black)
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
                            Text("Data: ${m.measuredAt}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Braço: ${m.armsCm ?: "-"}cm • Cintura: ${m.waistCm ?: "-"}cm • Peito: ${m.chestCm ?: "-"}cm",
                                fontSize = 11.sp,
                                color = TextoSecundario
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${m.weightKg ?: "-"} kg", fontWeight = FontWeight.Black, fontSize = 15.sp, color = VerdeEsmeralda)
                            if (m.bodyFatPct != null) {
                                Text("BF ${m.bodyFatPct}%", fontSize = 11.sp, color = CianoNeon)
                            }
                        }
                    }
                }
            }
        }
    }
}
