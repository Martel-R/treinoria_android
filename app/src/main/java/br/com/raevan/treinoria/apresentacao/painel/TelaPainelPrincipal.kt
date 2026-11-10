package br.com.raevan.treinoria.apresentacao.painel

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import br.com.raevan.treinoria.dados.remoto.dto.ExercicioDto
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.HabitoDiarioDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.SessaoTreinoDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.atualizacao.DialogoAtualizacaoApp
import br.com.raevan.treinoria.nucleo.atualizacao.GerenciadorAtualizacaoApp
import br.com.raevan.treinoria.nucleo.atualizacao.InfoVersaoRemota
import br.com.raevan.treinoria.nucleo.componentes.DialogoVideoExercicioNativo
import br.com.raevan.treinoria.nucleo.saude.GerenciadorHealthConnect
import br.com.raevan.treinoria.nucleo.saude.ResumoSaudeDiaria
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch

@Composable
fun TelaPainelPrincipal(
    aoIniciarTreinoDaFicha: (FichaTreinoDto) -> Unit,
    aoAbrirPersonalHub: () -> Unit,
    aoFazerLogout: () -> Unit
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var carregando by remember { mutableStateOf(true) }
    var perfil by remember { mutableStateOf<PerfilDto?>(null) }
    var fichas by remember { mutableStateOf<List<FichaTreinoDto>>(emptyList()) }
    var sessoes by remember { mutableStateOf<List<SessaoTreinoDto>>(emptyList()) }
    var habitoHoje by remember { mutableStateOf(HabitoDiarioDto()) }

    // Estado do Android Health Connect (Google Fit / Samsung Health / Smartwatches)
    var resumoSaude by remember { mutableStateOf(ResumoSaudeDiaria()) }
    var sincronizandoSaude by remember { mutableStateOf(false) }

    var exercicioVideoEmbutido by remember { mutableStateOf<ExercicioDto?>(null) }

    // Estado da Verificação Manual de Atualização do App (OTA)
    val (versaoInstaladaCodigo, versaoInstaladaNome) = remember {
        GerenciadorAtualizacaoApp.obterVersaoInstalada(contexto)
    }
    var verificandoAtualizacao by remember { mutableStateOf(false) }
    var infoAtualizacaoManual by remember { mutableStateOf<InfoVersaoRemota?>(null) }
    var mensagemStatusAtualizacao by remember { mutableStateOf<String?>(null) }

    var exibirCheckinIa by remember { mutableStateOf(false) }
    var energiaNivel by remember { mutableStateOf(8f) }
    var horasSono by remember { mutableStateOf("7.5") }
    var dorMuscular by remember { mutableStateOf("Nenhuma") }
    var respostaCoachIa by remember { mutableStateOf<String?>(null) }
    var consultandoIa by remember { mutableStateOf(false) }

    val atualizarResumoSaude = {
        escopo.launch {
            sincronizandoSaude = true
            resumoSaude = GerenciadorHealthConnect.lerResumoDiario(contexto)
            if (resumoSaude.horasSonoUltimaNoite > 0.0) {
                horasSono = resumoSaude.horasSonoUltimaNoite.toString()
            }
            sincronizandoSaude = false
        }
    }

    // Launcher oficial do Health Connect para pedir permissões de Saúde
    val permissoesSaudeLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        atualizarResumoSaude()
    }

    val recarregarDados = {
        escopo.launch {
            carregando = true
            try {
                RepositorioTreinoria.garantirDadosIniciaisUsuario()
                perfil = RepositorioTreinoria.carregarPerfil()
                fichas = RepositorioTreinoria.listarFichasTreino()
                sessoes = RepositorioTreinoria.listarSessoesRecentes(15)
                habitoHoje = RepositorioTreinoria.obterHabitoHoje()
                resumoSaude = GerenciadorHealthConnect.lerResumoDiario(contexto)
                if (resumoSaude.horasSonoUltimaNoite > 0.0) {
                    horasSono = resumoSaude.horasSonoUltimaNoite.toString()
                }
            } finally {
                carregando = false
            }
        }
    }

    LaunchedEffect(Unit) {
        recarregarDados()
    }

    val treinoDoDia = remember(fichas, sessoes) {
        RepositorioTreinoria.selecionarTreinoDoDia(fichas, sessoes)
    }
    val nomeDiaHoje = remember { RepositorioTreinoria.obterNomeDiaHoje() }

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

    if (infoAtualizacaoManual != null) {
        DialogoAtualizacaoApp(
            infoRemota = infoAtualizacaoManual!!,
            versaoInstaladaNome = versaoInstaladaNome,
            versaoInstaladaCodigo = versaoInstaladaCodigo,
            aoFechar = { infoAtualizacaoManual = null }
        )
    }

    if (exercicioVideoEmbutido != null && !exercicioVideoEmbutido?.videoUrl.isNullOrBlank()) {
        val ex = exercicioVideoEmbutido!!
        DialogoVideoExercicioNativo(
            tituloExercicio = ex.name,
            grupoMuscular = ex.muscleGroup,
            videoUrl = ex.videoUrl!!,
            instrucoes = ex.instructions,
            aoFechar = { exercicioVideoEmbutido = null }
        )
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
                        "Calibre as cargas de hoje com base na sua recuperação (sincronizado com Health Connect):",
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
                            val dadosSmartwatch = if (resumoSaude.permissoesConcedidas) {
                                "Dados Health Connect: ${resumoSaude.passosHoje} passos hoje, ${resumoSaude.caloriasAtivasKcal} kcal ativas, FC recente: ${resumoSaude.ultimoBatimentoBpm ?: "N/D"} bpm."
                            } else ""
                            respostaCoachIa = RepositorioTreinoria.consultarCoachIa(
                                "Atleta ${perfil?.name ?: ""}, objetivo ${perfil?.goal ?: "Hipertrofia"}. " +
                                    "Check-in pré-treino: Energia ${energiaNivel.toInt()}/10, Sono ${horasSono}h, Dor: $dorMuscular. $dadosSmartwatch " +
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
        // Cabeçalho do Atleta / Personal + Botão Verificar Atualização + Logout
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = "${perfil?.goal ?: "Hipertrofia"} • $xpTotal XP • v$versaoInstaladaNome (#$versaoInstaladaCodigo)",
                                    fontSize = 11.sp,
                                    color = TextoSecundario
                                )
                            }
                        }
                        IconButton(onClick = aoFazerLogout) {
                            Icon(Icons.Default.Logout, contentDescription = "Sair", tint = TextoSecundario)
                        }
                    }

                    // Botão Visível de Verificar Atualização do App (OTA)
                    FilledTonalButton(
                        enabled = !verificandoAtualizacao,
                        onClick = {
                            verificandoAtualizacao = true
                            mensagemStatusAtualizacao = null
                            escopo.launch {
                                val disponivel = GerenciadorAtualizacaoApp.verificarAtualizacaoDisponivel(contexto)
                                verificandoAtualizacao = false
                                if (disponivel != null) {
                                    infoAtualizacaoManual = disponivel
                                } else {
                                    val remota = GerenciadorAtualizacaoApp.consultarVersaoRemota()
                                    if (remota != null) {
                                        mensagemStatusAtualizacao = "✅ Seu app já está na versão mais recente (v$versaoInstaladaNome • Build #$versaoInstaladaCodigo)!"
                                    } else {
                                        mensagemStatusAtualizacao = "⚠️ Não foi possível consultar o servidor agora. Tente novamente em instantes."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (verificandoAtualizacao) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificando nova versão...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Verificar Atualização do App (v$versaoInstaladaNome)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CianoNeon
                            )
                        }
                    }

                    if (mensagemStatusAtualizacao != null) {
                        Text(
                            text = mensagemStatusAtualizacao!!,
                            fontSize = 11.sp,
                            color = VerdeEsmeraldaClaro,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Card Oficial: ANDROID HEALTH CONNECT (Google Fit / Samsung Health / Smartwatches)
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
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = VerdeEsmeralda,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Health Connect • Google Fit & Relógios",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = TextoPrimario
                                )
                                Text(
                                    text = if (resumoSaude.permissoesConcedidas)
                                        "Conectado • Sincronizando Treinos, Passos, BPM e Sono"
                                    else
                                        "Sincronize com Google Fit, Samsung Health, Garmin e Mi Fitness",
                                    fontSize = 11.sp,
                                    color = if (resumoSaude.permissoesConcedidas) VerdeEsmeraldaClaro else TextoSecundario
                                )
                            }
                        }

                        if (resumoSaude.permissoesConcedidas) {
                            IconButton(
                                onClick = { atualizarResumoSaude() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (sincronizandoSaude) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = CianoNeon)
                                }
                            }
                        }
                    }

                    if (resumoSaude.permissoesConcedidas) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuperficieElevada,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("👣 Passos", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        text = "${resumoSaude.passosHoje}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = VerdeEsmeralda
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuperficieElevada,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🔥 Ativas", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        text = "${resumoSaude.caloriasAtivasKcal} kcal",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = AmbarDestaque
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuperficieElevada,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("❤️ Pulso", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        text = if (resumoSaude.ultimoBatimentoBpm != null) "${resumoSaude.ultimoBatimentoBpm} bpm" else "-- bpm",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = VermelhoAlerta
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuperficieElevada,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("😴 Sono", fontSize = 10.sp, color = TextoSecundario)
                                    Text(
                                        text = if (resumoSaude.horasSonoUltimaNoite > 0) "${resumoSaude.horasSonoUltimaNoite}h" else "--",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = CianoNeon
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (resumoSaude.disponivelNoAparelho) {
                                    try {
                                        permissoesSaudeLauncher.launch(GerenciadorHealthConnect.PERMISSOES_SAUDE)
                                    } catch (_: Throwable) {
                                        GerenciadorHealthConnect.abrirConfiguracoesOuPlayStoreHealthConnect(contexto)
                                    }
                                } else {
                                    GerenciadorHealthConnect.abrirConfiguracoesOuPlayStoreHealthConnect(contexto)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (resumoSaude.precisaInstalarAppHealthConnect)
                                    "Instalar / Ativar Health Connect"
                                else
                                    "Conectar Google Fit & Smartwatch (Health Connect)",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
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
                                    "Gerencie seus alunos, monte fichas e envie via WhatsApp",
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

        // KPIs de Treino
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
                        Text("Meta: ${perfil?.weeklyGoal ?: 5}x/sem", fontSize = 10.sp, color = TextoSecundario)
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
                            "$volumeTotalAcumulado kg",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = CianoNeon
                        )
                        Text("Tonelagem acumulada", fontSize = 10.sp, color = TextoSecundario)
                    }
                }
            }
        }

        // Card Exclusivo: TREINO DO DIA (Apenas 1 ficha — a de hoje!)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🔥 Treino do Dia ($nomeDiaHoje)",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = VerdeEsmeralda
                )
                if (carregando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = aoAbrirPersonalHub) {
                        Text("Ver/Editar Fichas", fontSize = 12.sp, color = CianoNeon)
                    }
                }
            }
        }

        if (treinoDoDia != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SuperficieCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                treinoDoDia.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = VerdeEsmeraldaClaro
                            )
                            if (!treinoDoDia.description.isNullOrBlank()) {
                                Text(treinoDoDia.description, fontSize = 12.sp, color = TextoSecundario)
                            }
                            if (!treinoDoDia.dayOfWeek.isNullOrBlank()) {
                                Text(
                                    "📅 Programado: ${treinoDoDia.dayOfWeek}",
                                    fontSize = 11.sp,
                                    color = CianoNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (treinoDoDia.items.isNotEmpty()) {
                            HorizontalDivider(color = BordaCard.copy(alpha = 0.5f))
                            treinoDoDia.items.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !item.exercise?.videoUrl.isNullOrBlank()) {
                                            exercicioVideoEmbutido = item.exercise
                                        }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (!item.exercise?.videoUrl.isNullOrBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = "Assistir vídeo no app",
                                                tint = VerdeEsmeralda,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = item.exercise?.name ?: "Exercício",
                                            fontSize = 13.sp,
                                            color = TextoPrimario
                                        )
                                    }
                                    val pesoTxt = if ((item.targetWeightKg ?: 0.0) > 0) " (${item.targetWeightKg!!.toInt()}kg)" else ""
                                    Text(
                                        text = "${item.targetSets}x ${item.targetReps}$pesoTxt",
                                        fontSize = 12.sp,
                                        color = CianoNeon,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { aoIniciarTreinoDaFicha(treinoDoDia) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                modifier = Modifier.weight(2f).height(50.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Iniciar Treino do Dia", fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            OutlinedButton(
                                onClick = { exibirCheckinIa = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("IA", fontWeight = FontWeight.Bold, color = CianoNeon)
                            }
                        }
                    }
                }
            }
        }

        // Widget Diário de Hidratação (Água) + Creatina (Grava automaticamente no Health Connect!)
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
                            "${habitoHoje.waterMl} / ${habitoHoje.waterGoalMl} ml",
                            color = CianoNeon,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            (habitoHoje.waterMl.toFloat() / habitoHoje.waterGoalMl.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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
                                    GerenciadorHealthConnect.gravarAguaIngerida(contexto, 250)
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
                                    GerenciadorHealthConnect.gravarAguaIngerida(contexto, 500)
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
                                "${sessao.durationMinutes ?: 45} min • ${sessao.feeling ?: "Ótimo 🔥"}",
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
