package br.com.raevan.treinoria.apresentacao.personal

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.*
import br.com.raevan.treinoria.dados.repositorios.EstatisticaExercicioHistorico
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.dados.repositorios.StatusMuscularInfo
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.componentes.MapaCorporalFadigaCard
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Central de Avaliação de Desempenho & Evolução do Atleta + Acesso às Fichas de Treino
 * (e Carteira Multi-Alunos para Personal Trainers).
 */
@Composable
fun TelaPersonalEConstrutor() {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    // Inicialização Instantânea a partir do Cache em Memória (0ms!)
    var perfil by remember { mutableStateOf<PerfilDto?>(RepositorioTreinoria.cachePerfil) }
    var abaSelecionada by remember { mutableStateOf(0) } // 0 = Desempenho & Evolução, 1 = Minhas Fichas, 2 = Carteira Personal
    var alunos by remember {
        mutableStateOf<List<VinculoAlunoPersonalDto>>(RepositorioTreinoria.cacheAlunosPersonal)
    }
    var minhasFichas by remember {
        mutableStateOf<List<FichaTreinoDto>>(RepositorioTreinoria.cacheFichas)
    }
    var catalogoExercicios by remember {
        mutableStateOf<List<ExercicioDto>>(RepositorioTreinoria.cacheExercicios)
    }
    var sessoes by remember {
        mutableStateOf<List<SessaoTreinoDto>>(RepositorioTreinoria.cacheSessoes)
    }
    var estatisticasExercicios by remember {
        mutableStateOf<Map<String, EstatisticaExercicioHistorico>>(RepositorioTreinoria.cacheEstatisticasExercicios)
    }
    var mapaFadiga by remember {
        mutableStateOf<Map<String, StatusMuscularInfo>>(RepositorioTreinoria.cacheMapaFadiga)
    }
    var medidas by remember {
        mutableStateOf<List<MedidaCorporalDto>>(RepositorioTreinoria.cacheMedidas)
    }

    var avaliandoDesempenhoIa by remember { mutableStateOf(false) }
    var laudoDesempenhoIa by remember { mutableStateOf<String?>(null) }

    val roleSalvoLocal = remember {
        try { GerenciadorSessao.obterInstancia().obterRoleSalva() } catch (_: Exception) { "athlete" }
    }
    val ehPersonalOuAcademia = remember(perfil, roleSalvoLocal) {
        val role = (perfil?.accountRole ?: roleSalvoLocal).lowercase()
        role == "trainer" || role == "gym" || role == "admin"
    }

    // Modal Criar Nova Ficha (Com Gerador Inteligente por IA)
    var exibirModalNovaFicha by remember { mutableStateOf(false) }
    var alunoAlvoPrescricao by remember { mutableStateOf<VinculoAlunoPersonalDto?>(null) }
    var promptGeradorIaFicha by remember {
        mutableStateOf("Monte um treino de Costas e Bíceps focado em hipertrofia e largura dorsal para Terça e Sexta")
    }
    var gerandoFichaComIa by remember { mutableStateOf(false) }
    var explicacaoIaFicha by remember { mutableStateOf<String?>(null) }

    var tituloFicha by remember { mutableStateOf("Treino A • Peito, Ombro e Tríceps") }
    var subtituloFicha by remember { mutableStateOf("Hipertrofia Miofibrilar") }
    var diaSemanaFicha by remember { mutableStateOf("Segunda / Quinta") }
    var preTreinoSugeridoIa by remember { mutableStateOf("") }
    var posTreinoSugeridoIa by remember { mutableStateOf("") }
    var buscaExercicioModal by remember { mutableStateOf("") }
    val exerciciosEscolhidosIds = remember { mutableStateListOf<String>() }
    val pesosSugeridosPorExercicio = remember { mutableStateMapOf<String, Double>() }
    var salvandoFicha by remember { mutableStateOf(false) }
    var feedbackMensagem by remember { mutableStateOf<String?>(null) }

    var exibirModalNovoAluno by remember { mutableStateOf(false) }
    var nomeNovoAluno by remember { mutableStateOf("") }
    var emailNovoAluno by remember { mutableStateOf("") }
    var foneNovoAluno by remember { mutableStateOf("") }
    var objetivoNovoAluno by remember { mutableStateOf("Hipertrofia & Definição") }

    val recarregar = { forcar: Boolean ->
        escopo.launch {
            RepositorioTreinoria.garantirDadosIniciaisUsuario()
            perfil = RepositorioTreinoria.carregarPerfil(forcarAtualizacao = forcar)
            minhasFichas = RepositorioTreinoria.listarFichasTreino(forcarAtualizacao = forcar)
            sessoes = RepositorioTreinoria.listarSessoesRecentes(25, forcarAtualizacao = forcar)
            estatisticasExercicios = RepositorioTreinoria.obterEstatisticasExercicios(forcarAtualizacao = forcar)
            mapaFadiga = RepositorioTreinoria.calcularMapaFadigaMuscular(forcarAtualizacao = forcar)
            medidas = RepositorioTreinoria.listarMedidasCorporais(forcarAtualizacao = forcar)
            if (catalogoExercicios.isEmpty()) {
                catalogoExercicios = RepositorioTreinoria.listarExercicios(forcarAtualizacao = false)
            }
            val roleAtual = (perfil?.accountRole ?: roleSalvoLocal).lowercase()
            if (roleAtual == "trainer" || roleAtual == "gym" || roleAtual == "admin") {
                alunos = RepositorioTreinoria.listarAlunosPersonal(forcarAtualizacao = forcar)
            }
        }
    }

    LaunchedEffect(Unit) {
        recarregar(false)
    }

    // Métricas Consolidadas de Desempenho & Evolução
    val volumeTotalHistorico = remember(sessoes) { sessoes.sumOf { it.totalVolumeKg }.toInt() }
    val mediaVolumePorTreino = remember(sessoes) {
        if (sessoes.isNotEmpty()) (volumeTotalHistorico / sessoes.size) else 0
    }
    val duracaoMediaMin = remember(sessoes) {
        if (sessoes.isNotEmpty()) sessoes.mapNotNull { it.durationMinutes }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 45 else 0
    }
    val mapaNomesExercicios = remember(catalogoExercicios) {
        catalogoExercicios.associateBy { it.id }
    }
    val rankingPrs = remember(estatisticasExercicios, mapaNomesExercicios) {
        estatisticasExercicios.entries
            .map { (exId, st) ->
                val ex = mapaNomesExercicios[exId]
                val umRmEstimado = (st.maxWeightKg * (1.0 + (st.lastReps.coerceAtLeast(1) / 30.0))).roundToInt()
                Triple(ex?.name ?: "Exercício", st, umRmEstimado)
            }
            .sortedByDescending { it.second.maxWeightKg }
    }

    if (exibirModalNovoAluno && ehPersonalOuAcademia) {
        AlertDialog(
            onDismissRequest = { exibirModalNovoAluno = false },
            title = { Text("➕ Cadastrar Aluno na Consultoria", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nomeNovoAluno,
                        onValueChange = { nomeNovoAluno = it },
                        label = { Text("Nome do Aluno *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = foneNovoAluno,
                        onValueChange = { foneNovoAluno = it },
                        label = { Text("WhatsApp com DDD *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emailNovoAluno,
                        onValueChange = { emailNovoAluno = it },
                        label = { Text("E-mail do Aluno") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = objetivoNovoAluno,
                        onValueChange = { objetivoNovoAluno = it },
                        label = { Text("Objetivo Principal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = nomeNovoAluno.isNotBlank(),
                    onClick = {
                        escopo.launch {
                            RepositorioTreinoria.cadastrarAlunoPersonal(
                                nome = nomeNovoAluno,
                                email = emailNovoAluno,
                                telefone = foneNovoAluno,
                                objetivo = objetivoNovoAluno,
                                notas = "Cadastrado via App Android"
                            )
                            val trainerId = RepositorioTreinoria.obterUserIdAtual() ?: ""
                            if (foneNovoAluno.isNotBlank()) {
                                RepositorioTreinoria.enviarMensagemWhatsapp(
                                    numero = foneNovoAluno,
                                    texto = "🏋️ *Convite Oficial de Consultoria • TreinorIA PRO*\n\n" +
                                        "Olá, *${nomeNovoAluno.trim()}*! Você foi vinculado à minha consultoria no *TreinorIA*.\n" +
                                        "Acesse seus treinos com vídeos HD e acompanhamento de cargas em:\n" +
                                        "👉 https://treinoria.raevan.com.br/?trainer=$trainerId"
                                )
                            }
                            nomeNovoAluno = ""
                            foneNovoAluno = ""
                            emailNovoAluno = ""
                            exibirModalNovoAluno = false
                            alunos = RepositorioTreinoria.cacheAlunosPersonal
                        }
                    }
                ) {
                    Text("Salvar & Enviar Convite WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { exibirModalNovoAluno = false }) { Text("Cancelar") }
            }
        )
    }

    if (exibirModalNovaFicha) {
        val exerciciosFiltrados = catalogoExercicios.filter {
            buscaExercicioModal.isBlank() ||
                it.name.contains(buscaExercicioModal, ignoreCase = true) ||
                it.muscleGroup.contains(buscaExercicioModal, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { exibirModalNovaFicha = false },
            title = {
                Text(
                    text = if (alunoAlvoPrescricao != null) "Prescrever Ficha p/ ${alunoAlvoPrescricao!!.studentName}" else "Montar Ficha (Manual ou Coach IA)",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 490.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VerdeEsmeralda.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🤖 Gerar Ficha com IA (Analisa seu Perfil, Medidas, PRs e Histórico)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = VerdeEsmeralda
                            )
                            OutlinedTextField(
                                value = promptGeradorIaFicha,
                                onValueChange = { promptGeradorIaFicha = it },
                                label = { Text("Peça à IA (ex: Treino de Pernas completo p/ Quarta)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                enabled = !gerandoFichaComIa && promptGeradorIaFicha.isNotBlank(),
                                onClick = {
                                    gerandoFichaComIa = true
                                    explicacaoIaFicha = null
                                    escopo.launch {
                                        val gerada = RepositorioTreinoria.gerarFichaCompletaComIa(
                                            context = contexto,
                                            pedidoUsuario = promptGeradorIaFicha,
                                            catalogoExercicios = catalogoExercicios
                                        )
                                        if (gerada != null) {
                                            tituloFicha = gerada.planName
                                            subtituloFicha = gerada.description
                                            diaSemanaFicha = gerada.dayOfWeek
                                            preTreinoSugeridoIa = gerada.preWorkoutMeal
                                            posTreinoSugeridoIa = gerada.postWorkoutMeal
                                            explicacaoIaFicha = gerada.replyMessage
                                            exerciciosEscolhidosIds.clear()
                                            pesosSugeridosPorExercicio.clear()
                                            gerada.items.forEach { itIa ->
                                                val existe = catalogoExercicios.any { c -> c.id == itIa.exerciseId }
                                                if (existe && !exerciciosEscolhidosIds.contains(itIa.exerciseId)) {
                                                    exerciciosEscolhidosIds.add(itIa.exerciseId)
                                                    pesosSugeridosPorExercicio[itIa.exerciseId] = itIa.targetWeightKg
                                                }
                                            }
                                        }
                                        gerandoFichaComIa = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                if (gerandoFichaComIa) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("IA analisando perfil e montando ficha...", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Preencher Ficha Automaticamente com IA", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            if (explicacaoIaFicha != null) {
                                Text(
                                    text = "💡 ${explicacaoIaFicha!!}",
                                    fontSize = 11.sp,
                                    color = VerdeEsmeraldaClaro
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tituloFicha,
                        onValueChange = { tituloFicha = it },
                        label = { Text("Título da Ficha") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = subtituloFicha,
                            onValueChange = { subtituloFicha = it },
                            label = { Text("Foco / Subtítulo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = diaSemanaFicha,
                            onValueChange = { diaSemanaFicha = it },
                            label = { Text("Dias") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = buscaExercicioModal,
                        onValueChange = { buscaExercicioModal = it },
                        label = { Text("Buscar nos 69 exercícios (${exerciciosEscolhidosIds.size} selecionados)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(exerciciosFiltrados, key = { it.id }) { ex ->
                            val marcado = exerciciosEscolhidosIds.contains(ex.id)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (marcado) VerdeEsmeralda.copy(alpha = 0.2f) else SuperficieElevada,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ex.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${ex.muscleGroup} • ${ex.equipment}", fontSize = 10.sp, color = TextoSecundario)
                                    }
                                    Checkbox(
                                        checked = marcado,
                                        onCheckedChange = {
                                            if (marcado) exerciciosEscolhidosIds.remove(ex.id)
                                            else exerciciosEscolhidosIds.add(ex.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !salvandoFicha && tituloFicha.isNotBlank() && exerciciosEscolhidosIds.isNotEmpty(),
                    onClick = {
                        salvandoFicha = true
                        escopo.launch {
                            val targetUid = alunoAlvoPrescricao?.studentId
                                ?: RepositorioTreinoria.obterUserIdAtual()
                                ?: ""

                            val triplas = exerciciosEscolhidosIds.map { id ->
                                Triple(id, 4, "8-12")
                            }

                            RepositorioTreinoria.criarFichaComExercicios(
                                targetUserId = targetUid,
                                titulo = tituloFicha,
                                subtitulo = subtituloFicha,
                                diaSemana = diaSemanaFicha,
                                preWorkoutMeal = preTreinoSugeridoIa,
                                postWorkoutMeal = posTreinoSugeridoIa,
                                exerciciosSelecionados = triplas,
                                pesosAlvo = pesosSugeridosPorExercicio.toMap()
                            )

                            val foneAluno = alunoAlvoPrescricao?.studentWhatsapp
                            if (!foneAluno.isNullOrBlank()) {
                                val nomesEx = catalogoExercicios
                                    .filter { exerciciosEscolhidosIds.contains(it.id) }
                                    .joinToString("\n") { "• *${it.name}* (4x 8-12)" }

                                RepositorioTreinoria.enviarMensagemWhatsapp(
                                    numero = foneAluno,
                                    texto = "🔥 *TREINORIA PRO • Nova Ficha Prescrita!*\n\n" +
                                        "Olá, *${alunoAlvoPrescricao?.studentName}*! Seu Personal Trainer acabou de liberar sua nova ficha:\n\n" +
                                        "📋 *$tituloFicha* ($diaSemanaFicha)\n" +
                                        "$nomesEx\n\n" +
                                        "👉 Acesse com vídeos de execução em:\nhttps://treinoria.raevan.com.br"
                                )
                            }

                            salvandoFicha = false
                            exibirModalNovaFicha = false
                            exerciciosEscolhidosIds.clear()
                            minhasFichas = RepositorioTreinoria.cacheFichas
                            feedbackMensagem = "✅ Ficha salva e sincronizada com sucesso!"
                        }
                    }
                ) {
                    Text(if (salvandoFicha) "Salvando..." else "Salvar Ficha (${exerciciosEscolhidosIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { exibirModalNovaFicha = false }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // =====================================================================
        // SELETOR SUPERIOR: [📊 Desempenho & Evolução] | [📋 Minhas Fichas] | ([👥 Alunos] se Personal)
        // =====================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = abaSelecionada == 0,
                    onClick = { abaSelecionada = 0 },
                    label = {
                        Text(
                            "📊 Desempenho & Evolução",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(1.25f)
                )

                FilterChip(
                    selected = abaSelecionada == 1,
                    onClick = { abaSelecionada = 1 },
                    label = {
                        Text(
                            "📋 Fichas (${minhasFichas.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                if (ehPersonalOuAcademia) {
                    FilterChip(
                        selected = abaSelecionada == 2,
                        onClick = { abaSelecionada = 2 },
                        label = {
                            Text(
                                "👥 Alunos (${alunos.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.weight(0.95f)
                    )
                }
            }
        }

        if (feedbackMensagem != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VerdeEsmeralda.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedbackMensagem!!,
                        color = VerdeEsmeralda,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // =====================================================================
        // ABA 0: AMBIENTE PRINCIPAL DE AVALIAÇÃO DE DESEMPENHO & EVOLUÇÃO DO USUÁRIO
        // =====================================================================
        if (abaSelecionada == 0) {
            // Card de Acesso Rápido às Fichas + Botão de Avaliação de Desempenho com IA
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
                                    text = "📈 Central de Desempenho & Evolução",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = VerdeEsmeralda
                                )
                                Text(
                                    text = "Acompanhe sua evolução de cargas, 1RM estimado, PRs, volume semanal e composição corporal",
                                    fontSize = 11.sp,
                                    color = TextoSecundario
                                )
                            }
                            FilledTonalButton(
                                onClick = { abaSelecionada = 1 },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Assignment, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Acessar Fichas", fontSize = 11.sp, fontWeight = FontWeight.Black, color = CianoNeon)
                            }
                        }

                        Button(
                            enabled = !avaliandoDesempenhoIa,
                            onClick = {
                                avaliandoDesempenhoIa = true
                                escopo.launch {
                                    laudoDesempenhoIa = RepositorioTreinoria.consultarCoachIa(
                                        promptUsuario = "Faça uma Avaliação Completa de Desempenho e Evolução deste atleta em 4 tópicos curtos: " +
                                            "(1) Evolução de Força e PRs; (2) Equilíbrio de Volume Semanal entre os músculos; " +
                                            "(3) Evolução Corporal/Nutrição; e (4) Próxima Meta de Sobrecarga Progressiva para esta semana.",
                                        context = contexto
                                    )
                                    avaliandoDesempenhoIa = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (avaliandoDesempenhoIa) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Coach IA gerando Laudo de Desempenho...", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gerar Diagnóstico de Desempenho & Evolução com IA", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        if (laudoDesempenhoIa != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FundoAcademia,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = laudoDesempenhoIa!!,
                                    fontSize = 12.sp,
                                    color = TextoPrimario,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4 KPIs de Performance Atlética
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuperficieCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sessões", fontSize = 10.sp, color = TextoSecundario)
                            Text(
                                "${sessoes.size}",
                                fontSize = 20.sp,
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
                            Text("Tonelagem", fontSize = 10.sp, color = TextoSecundario)
                            Text(
                                "$volumeTotalHistorico kg",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = CianoNeon
                            )
                            Text("Média: $mediaVolumePorTreino kg/t", fontSize = 10.sp, color = TextoSecundario)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuperficieCard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Tempo Médio", fontSize = 10.sp, color = TextoSecundario)
                            Text(
                                "$duracaoMediaMin min",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = AmbarDestaque
                            )
                            Text("${rankingPrs.size} exercícios c/ PR", fontSize = 10.sp, color = TextoSecundario)
                        }
                    }
                }
            }

            // Gráfico de Barras de Evolução de Volume por Sessão (Últimos Treinos)
            if (sessoes.isNotEmpty()) {
                item {
                    val ultimasSessoesCronologicas = sessoes.take(7).reversed()
                    val maiorVolume = (ultimasSessoesCronologicas.maxOfOrNull { it.totalVolumeKg } ?: 1.0).coerceAtLeast(1.0)

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SuperficieCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📊 Curva de Progressão de Volume por Treino",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = TextoPrimario
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                ultimasSessoesCronologicas.forEach { s ->
                                    val frac = (s.totalVolumeKg / maiorVolume).toFloat().coerceIn(0.15f, 1f)
                                    val dataCurta = if (s.startedAt.length >= 10) {
                                        "${s.startedAt.substring(8, 10)}/${s.startedAt.substring(5, 7)}"
                                    } else "Hoje"

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        Text(
                                            text = "${s.totalVolumeKg.toInt()}kg",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CianoNeon
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .fillMaxHeight(frac * 0.72f)
                                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                .background(VerdeEsmeralda)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = dataCurta,
                                            fontSize = 9.sp,
                                            color = TextoSecundario
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ranking de Força Máxima, Recordes Pessoais (PRs) e 1RM Estimado (Fórmula de Epley)
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
                            Text(
                                text = "🏆 Recordes Pessoais (PRs) & 1RM Estimado",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = AmbarDestaque
                            )
                            Text(
                                text = "Fórmula de Epley",
                                fontSize = 10.sp,
                                color = TextoSecundario
                            )
                        }

                        if (rankingPrs.isEmpty()) {
                            Text(
                                text = "Conclua seu primeiro treino para destravar seus Recordes Pessoais (PRs) e cálculo de 1RM!",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        } else {
                            rankingPrs.take(10).forEachIndexed { idx, (nomeEx, st, umRm) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = FundoAcademia,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${idx + 1}. $nomeEx",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TextoPrimario
                                            )
                                            Text(
                                                text = "Última: ${st.lastWeightKg.toInt()}kg × ${st.lastReps} reps",
                                                fontSize = 11.sp,
                                                color = TextoSecundario
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "PR: ${st.maxWeightKg.toInt()} kg",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = AmbarDestaque
                                            )
                                            Text(
                                                text = "1RM Est.: $umRm kg",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CianoNeon
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mapa Corporal 2D de Fadiga & Volume Semanal por Grupo Muscular
            item {
                MapaCorporalFadigaCard(mapaStatus = mapaFadiga)
            }

            // Evolução Antropométrica Recente (Peso, BF%, Medidas)
            if (medidas.isNotEmpty()) {
                item {
                    val mAtual = medidas.first()
                    val mAnterior = medidas.getOrNull(1)
                    val diffPeso = if (mAtual.weightKg != null && mAnterior?.weightKg != null) {
                        mAtual.weightKg - mAnterior.weightKg
                    } else null

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = SuperficieCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📏 Evolução Corporal & Antropometria",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = CianoNeon
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Peso Atual: ${mAtual.weightKg ?: "-"} kg" +
                                        (if (diffPeso != null) " (${if (diffPeso >= 0) "+" else ""}${String.format("%.1f", diffPeso)} kg)" else ""),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = VerdeEsmeralda
                                )
                                Text(
                                    text = "BF: ${mAtual.bodyFatPct ?: "-"}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AmbarDestaque
                                )
                            }
                            Text(
                                text = "Peitoral: ${mAtual.chestCm ?: "-"}cm • Cintura: ${mAtual.waistCm ?: "-"}cm • Braço: ${mAtual.armsCm ?: "-"}cm • Coxa: ${mAtual.legsCm ?: "-"}cm",
                                fontSize = 11.sp,
                                color = TextoSecundario
                            )
                        }
                    }
                }
            }
        } else if (abaSelecionada == 1) {
            // =================================================================
            // ABA 1: MINHAS FICHAS DE TREINO (Criação Manual ou com Coach IA)
            // =================================================================
            item {
                Button(
                    onClick = {
                        alunoAlvoPrescricao = null
                        exerciciosEscolhidosIds.clear()
                        explicacaoIaFicha = null
                        exibirModalNovaFicha = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Montar Nova Ficha (Coach IA ou Manual)", fontWeight = FontWeight.Black)
                }
            }

            items(minhasFichas, key = { it.id }) { ficha ->
                Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ficha.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = VerdeEsmeralda)
                                Text(
                                    "${ficha.description ?: "Hipertrofia"} • ${ficha.dayOfWeek ?: "Livre"} • ${ficha.items.size} exercícios",
                                    fontSize = 12.sp,
                                    color = TextoSecundario
                                )
                            }
                            IconButton(
                                onClick = {
                                    escopo.launch {
                                        RepositorioTreinoria.excluirFicha(ficha.id)
                                        minhasFichas = RepositorioTreinoria.cacheFichas
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = VermelhoAlerta)
                            }
                        }

                        if (ficha.items.isNotEmpty()) {
                            HorizontalDivider(color = BordaCard.copy(alpha = 0.5f))
                            ficha.items.forEach { item ->
                                Text(
                                    text = "• ${item.exercise?.name ?: "Exercício"} (${item.targetSets}x ${item.targetReps})",
                                    fontSize = 11.sp,
                                    color = TextoPrimario
                                )
                            }
                        }
                    }
                }
            }
        } else if (ehPersonalOuAcademia && abaSelecionada == 2) {
            // =================================================================
            // ABA 2: CARTEIRA MULTI-ALUNOS DO PERSONAL TRAINER
            // =================================================================
            item {
                Button(
                    onClick = { exibirModalNovoAluno = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CianoNeon),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Adicionar Novo Aluno & Enviar Link WhatsApp", fontWeight = FontWeight.Black)
                }
            }

            if (alunos.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(38.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Sua Carteira de Alunos está pronta", fontWeight = FontWeight.Bold)
                            Text(
                                "Adicione seus alunos de consultoria presencial ou online para montar fichas personalizadas e enviar direto no WhatsApp.",
                                fontSize = 12.sp,
                                color = TextoSecundario
                            )
                        }
                    }
                }
            }

            items(alunos, key = { it.id }) { aluno ->
                Surface(shape = RoundedCornerShape(16.dp), color = SuperficieCard, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(aluno.studentName, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(
                                    "🎯 ${aluno.studentGoal} • 📱 ${aluno.studentWhatsapp ?: "Sem WhatsApp"}",
                                    fontSize = 12.sp,
                                    color = TextoSecundario
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VerdeEsmeralda.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "ATIVO",
                                    color = VerdeEsmeraldaClaro,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    alunoAlvoPrescricao = aluno
                                    exerciciosEscolhidosIds.clear()
                                    exibirModalNovaFicha = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Prescrever Ficha", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (!aluno.studentWhatsapp.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        escopo.launch {
                                            RepositorioTreinoria.enviarMensagemWhatsapp(
                                                numero = aluno.studentWhatsapp,
                                                texto = "💪 *TreinorIA PRO • Acompanhamento do Personal*\n\n" +
                                                    "Fala, *${aluno.studentName}*! Como estão os treinos desta semana? " +
                                                    "Não esqueça de registrar suas cargas no app: https://treinoria.raevan.com.br"
                                            )
                                            feedbackMensagem = "✅ Mensagem enviada no WhatsApp de ${aluno.studentName}!"
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = CianoNeon, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("WhatsApp", fontSize = 12.sp, color = CianoNeon, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
