package br.com.raevan.treinoria.apresentacao.personal

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.ExercicioDto
import br.com.raevan.treinoria.dados.remoto.dto.FichaTreinoDto
import br.com.raevan.treinoria.dados.remoto.dto.PerfilDto
import br.com.raevan.treinoria.dados.remoto.dto.VinculoAlunoPersonalDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.launch

@Composable
fun TelaPersonalEConstrutor() {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    // Inicializa instantaneamente a partir do Cache em Memória!
    var perfil by remember { mutableStateOf<PerfilDto?>(RepositorioTreinoria.cachePerfil) }
    var abaSelecionada by remember { mutableStateOf(0) }
    var alunos by remember {
        mutableStateOf<List<VinculoAlunoPersonalDto>>(RepositorioTreinoria.cacheAlunosPersonal)
    }
    var minhasFichas by remember {
        mutableStateOf<List<FichaTreinoDto>>(RepositorioTreinoria.cacheFichas)
    }
    var catalogoExercicios by remember {
        mutableStateOf<List<ExercicioDto>>(RepositorioTreinoria.cacheExercicios)
    }
    var carregando by remember {
        mutableStateOf(RepositorioTreinoria.cacheFichas.isEmpty())
    }

    val roleSalvoLocal = remember {
        try { GerenciadorSessao.obterInstancia().obterRoleSalva() } catch (_: Exception) { "athlete" }
    }
    val ehPersonalOuAcademia = remember(perfil, roleSalvoLocal) {
        val role = (perfil?.accountRole ?: roleSalvoLocal).lowercase()
        role == "trainer" || role == "gym" || role == "admin"
    }

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
            if (minhasFichas.isEmpty()) carregando = true
            RepositorioTreinoria.garantirDadosIniciaisUsuario()
            perfil = RepositorioTreinoria.carregarPerfil(forcarAtualizacao = forcar)
            minhasFichas = RepositorioTreinoria.listarFichasTreino(forcarAtualizacao = forcar)
            val roleAtual = (perfil?.accountRole ?: roleSalvoLocal).lowercase()
            if (roleAtual == "trainer" || roleAtual == "gym" || roleAtual == "admin") {
                alunos = RepositorioTreinoria.listarAlunosPersonal(forcarAtualizacao = forcar)
            } else {
                abaSelecionada = 0
                alunos = emptyList()
            }
            if (catalogoExercicios.isEmpty()) {
                catalogoExercicios = RepositorioTreinoria.listarExercicios(forcarAtualizacao = false)
            }
            carregando = false
        }
    }

    LaunchedEffect(Unit) {
        recarregar(false)
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
                            recarregar(false)
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
        if (ehPersonalOuAcademia) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = abaSelecionada == 0,
                        onClick = { abaSelecionada = 0 },
                        label = { Text("📋 Minhas Fichas (${minhasFichas.size})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = abaSelecionada == 1,
                        onClick = { abaSelecionada = 1 },
                        label = { Text("👥 Carteira Personal (${alunos.size})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "📋 Minhas Fichas de Treino (${minhasFichas.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = VerdeEsmeralda
                )
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

        if (abaSelecionada == 0 || !ehPersonalOuAcademia) {
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
        } else if (ehPersonalOuAcademia) {
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
