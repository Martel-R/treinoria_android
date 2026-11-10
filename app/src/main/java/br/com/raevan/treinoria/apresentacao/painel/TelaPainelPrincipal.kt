package br.com.raevan.treinoria.apresentacao.painel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.HabitoDiarioDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.SessaoTreinoDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch

@Composable
fun TelaPainelPrincipal(
    aoIniciarTreinoDaFicha: (FichaTreinoDto) -> Unit,
    aoAbrirPersonalHub: () -> Unit,
    aoAbrirCatalogo: () -> Unit,
    aoFazerLogout: () -> Unit
) {
    val escopo = rememberCoroutineScope()
    var carregando by remember { mutableStateOf(true) }
    var perfil by remember { mutableStateOf<PerfilDto?>(null) }
    var fichas by remember { mutableStateOf<List<FichaTreinoDto>>(emptyList()) }
    var sessoes by remember { mutableStateOf<List<SessaoTreinoDto>>(emptyList()) }
    var habitoHoje by remember { mutableStateOf(HabitoDiarioDto()) }

    var exibirCheckinIa by remember { mutableStateOf(false) }
    var energiaNivel by remember { mutableStateOf(8f) }
    var horasSono by remember { mutableStateOf("7.5") }
    var dorMuscular by remember { mutableStateOf("Nenhuma") }
    var respostaCoachIa by remember { mutableStateOf<String?>(null) }
    var consultandoIa by remember { mutableStateOf(false) }

    val recarregarDados = {
        escopo.launch {
            carregando = true
            try {
                RepositorioTreinoria.garantirDadosIniciaisUsuario()
                perfil = RepositorioTreinoria.carregarPerfil()
                fichas = RepositorioTreinoria.listarFichasTreino()
                sessoes = RepositorioTreinoria.listarSessoesRecentes(15)
                habitoHoje = RepositorioTreinoria.obterHabitoHoje()
            } finally {
                carregando = false
            }
        }
    }

    LaunchedEffect(Unit) {
        recarregarDados()
    }

    val volumeTotalAcumulado = remember(sessoes) {
        sessoes.sumOf { it.totalVolumeKg }.toInt()
    }

    val xpTotal = remember(sessoes, habitoHoje) {
        (sessoes.size * 150) + (if (habitoHoje.creatineTaken) 40 else 0) + ((habitoHoje.waterMl / 500) * 15)
    }

    val patenteAtleta = remember(xpTotal) {
        when {
            xpTotal >= 3000 -> "🏆 Lenda PRO"
            xpTotal >= 1500 -> "💎 Diamante"
            xpTotal >= 600 -> "🥇 Ouro"
            xpTotal >= 250 -> "🥈 Prata"
            else -> "🥉 Bronze"
        }
    }

    if (exibirCheckinIa) {
        AlertDialog(
            onDismissRequest = { exibirCheckinIa = false },
            title = {
                Text("🤖 Check-in Pré-Treino Coach IA", fontWeight = FontWeight.Black, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Calibre as cargas de hoje com base na sua recuperação:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Energia Hoje: ${energiaNivel.toInt()}/10", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = energiaNivel,
                        onValueChange = { energiaNivel = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                    OutlinedTextField(
                        value = horasSono,
                        onValueChange = { horasSono = it },
                        label = { Text("Horas de Sono na última noite") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dorMuscular,
                        onValueChange = { dorMuscular = it },
                        label = { Text("Dor articular/muscular hoje?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (consultandoIa) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Coach IA analisando ajuste de carga...", fontSize = 12.sp)
                        }
                    }
                    if (respostaCoachIa != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerdeEsmeralda.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = respostaCoachIa!!,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !consultandoIa,
                    onClick = {
                        consultandoIa = true
                        escopo.launch {
                            respostaCoachIa = RepositorioTreinoria.consultarCoachIa(
                                "Atleta ${perfil?.name ?: ""}, objetivo ${perfil?.goal ?: "Hipertrofia"}. " +
                                    "Check-in pré-treino: Energia ${energiaNivel.toInt()}/10, Sono ${horasSono}h, Dor: $dorMuscular. " +
                                    "Recomende em até 4 linhas o ajuste percentual de carga, RPE alvo e aquecimento para o treino de hoje."
                            )
                            consultandoIa = false
                        }
                    }
                ) {
                    Text("Calibrar Treino com IA")
                }
            },
            dismissButton = {
                TextButton(onClick = { exibirCheckinIa = false }) { Text("Fechar") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Cabeçalho do Atleta / Personal
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VerdeEsmeralda.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = VerdeEsmeralda)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = perfil?.name ?: "Atleta TreinorIA",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (perfil?.accountRole == "trainer") CianoNeon.copy(alpha = 0.2f) else VerdeEsmeralda.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (perfil?.accountRole == "trainer") "PERSONAL" else patenteAtleta,
                                        color = if (perfil?.accountRole == "trainer") CianoNeon else VerdeEsmeraldaClaro,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${perfil?.goal ?: "Hipertrofia & Força"} • $xpTotal XP",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        }
                    }
                    IconButton(onClick = aoFazerLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Sair", tint = TextoSecundario)
                    }
                }
            }
        }

        // Atalho Rápido se for Personal Trainer
        if (perfil?.accountRole == "trainer") {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CianoNeon.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { aoAbrirPersonalHub() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Central do Personal Trainer (Multi-Alunos)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = CianoNeon
                                )
                                Text(
                                    "Gerencie seus alunos, monte fichas A/B/C/D e envie via WhatsApp",
                                    fontSize = 11.sp,
                                    color = TextoPrimario
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CianoNeon)
                    }
                }
            }
        }

        // KPIs de Treino + Botão Check-in Pré-Treino IA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SuperficieCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Treinos Feitos", fontSize = 11.sp, color = TextoSecundario)
                        Text(
                            "${sessoes.size}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = VerdeEsmeralda
                        )
                        Text("Meta: ${perfil?.weeklyGoal ?: 4}x/sem", fontSize = 10.sp, color = TextoSecundario)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SuperficieCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Volume Total", fontSize = 11.sp, color = TextoSecundario)
                        Text(
                            "${volumeTotalAcumulado} kg",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CianoNeon
                        )
                        Text("Tonelagem acumulada", fontSize = 10.sp, color = TextoSecundario)
                    }
                }
            }
        }

        // Card Check-in Pré-Treino IA & Catálogo 69 Exercícios
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { exibirCheckinIa = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check-in IA", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = aoAbrirCatalogo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("69 Vídeos 4K", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CianoNeon)
                }
            }
        }

        // Widget Diário de Hidratação (Água) + Creatina
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = CianoNeon)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Hidratação & Creatina Hoje",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            "${habitoHoje.waterMl} / ${perfil?.waterGoalMl ?: 3000} ml",
                            color = CianoNeon,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            (habitoHoje.waterMl.toFloat() / (perfil?.waterGoalMl ?: 3000).toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = CianoNeon
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                escopo.launch {
                                    habitoHoje = RepositorioTreinoria.atualizarHabitoHoje(
                                        habitoHoje.waterMl + 250,
                                        habitoHoje.creatineTaken
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+250ml 💧", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = {
                                escopo.launch {
                                    habitoHoje = RepositorioTreinoria.atualizarHabitoHoje(
                                        habitoHoje.waterMl + 500,
                                        habitoHoje.creatineTaken
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+500ml 💧", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        FilterChip(
                            selected = habitoHoje.creatineTaken,
                            onClick = {
                                escopo.launch {
                                    habitoHoje = RepositorioTreinoria.atualizarHabitoHoje(
                                        habitoHoje.waterMl,
                                        !habitoHoje.creatineTaken
                                    )
                                }
                            },
                            label = {
                                Text(
                                    if (habitoHoje.creatineTaken) "Creatina OK ✅" else "Creatina 5g",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }
            }
        }

        // Seção Minhas Fichas de Treino (Splits A/B/C/D)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Minhas Fichas de Treino (${fichas.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                if (carregando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }

        items(fichas, key = { it.id }) { ficha ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(ficha.title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = VerdeEsmeraldaClaro)
                                if (!ficha.dayOfWeek.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuperficieElevada
                                    ) {
                                        Text(
                                            ficha.dayOfWeek,
                                            fontSize = 10.sp,
                                            color = TextoSecundario,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (!ficha.subtitle.isNullOrBlank()) {
                                Text(ficha.subtitle, fontSize = 12.sp, color = TextoSecundario)
                            }
                        }
                        Button(
                            onClick = { aoIniciarTreinoDaFicha(ficha) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Treinar", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }

                    if (ficha.templateExercises.isNotEmpty()) {
                        HorizontalDivider(color = BordaCard.copy(alpha = 0.5f))
                        ficha.templateExercises.take(5).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${item.exercise?.name ?: "Exercício"}",
                                    fontSize = 12.sp,
                                    color = TextoPrimario,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${item.targetSets}x ${item.targetReps} (${item.targetWeightKg.toInt()}kg)",
                                    fontSize = 11.sp,
                                    color = CianoNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (ficha.templateExercises.size > 5) {
                            Text(
                                text = "+ ${ficha.templateExercises.size - 5} exercícios na ficha",
                                fontSize = 11.sp,
                                color = TextoSecundario
                            )
                        }
                    }
                }
            }
        }

        // Histórico Recente de Sessões
        if (sessoes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Últimos Treinos Concluídos", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            items(sessoes.take(5), key = { it.id }) { sessao ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sessao.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${sessao.totalSets} séries • ${(sessao.durationSeconds / 60).coerceAtLeast(1)} min • ${sessao.feeling ?: "Forte 🔥"}",
                                fontSize = 11.sp,
                                color = TextoSecundario
                            )
                        }
                        Text(
                            "${sessao.totalVolumeKg.toInt()} kg",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = VerdeEsmeralda
                        )
                    }
                }
            }
        }
    }
}
