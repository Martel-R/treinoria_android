package br.com.raevan.treinoria.apresentacao.nutricao

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

@Composable
fun TelaNutricaoEvolucao() {
    val escopo = rememberCoroutineScope()
    var abaInterna by remember { mutableStateOf(0) } // 0 = Nutrição IA, 1 = Medidas & Evolução

    var refeicoes by remember { mutableStateOf<List<RefeicaoLogDto>>(emptyList()) }
    var medidas by remember { mutableStateOf<List<MedidaCorporalDto>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Campos Nutrição IA
    var descricaoPrato by remember { mutableStateOf("") }
    var analisandoPrato by remember { mutableStateOf(false) }

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

    val kcalHoje = remember(refeicoes) { refeicoes.take(5).sumOf { it.calories } }
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
                    label = { Text("🍽️ Scanner Nutricional IA", fontWeight = FontWeight.Bold) },
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
                            "Analisar Refeição / Prato com Coach IA",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = VerdeEsmeralda
                        )
                        Text(
                            "Descreva seu prato (ex: 200g de arroz, 180g de peito de frango grelhado e 2 ovos) e a IA calcula calorias, proteínas, carboidratos e gorduras automaticamente:",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )
                        OutlinedTextField(
                            value = descricaoPrato,
                            onValueChange = { descricaoPrato = it },
                            label = { Text("O que você comeu?") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = !analisandoPrato && descricaoPrato.isNotBlank(),
                            onClick = {
                                analisandoPrato = true
                                escopo.launch {
                                    val resp = RepositorioTreinoria.consultarCoachIa(
                                        "Estime os macronutrientes desta refeição: '$descricaoPrato'. " +
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
                                        titulo = descricaoPrato.trim(),
                                        calorias = kcal,
                                        proteinaG = prot,
                                        carboG = carb,
                                        gorduraG = fat,
                                        feedbackIa = fb
                                    )
                                    descricaoPrato = ""
                                    analisandoPrato = false
                                    recarregar()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (analisandoPrato) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Calcular Macros com IA & Salvar", fontWeight = FontWeight.Black)
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
                            Text(ref.mealTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${ref.calories} kcal", fontWeight = FontWeight.Black, color = AmbarDestaque, fontSize = 14.sp)
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
                                "Braço: ${m.armCm ?: "-"}cm • Cintura: ${m.waistCm ?: "-"}cm • Peito: ${m.chestCm ?: "-"}cm",
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
