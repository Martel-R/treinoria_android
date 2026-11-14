package br.com.raevan.treinoria.nucleo.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class NivelHumorPosTreino(
    val id: String,
    val rotulo: String,
    val corHex: Color,
    val emocoes: List<String>
)

val NIVEIS_HUMOR_POS_TREINO = listOf(
    NivelHumorPosTreino(
        id = "excelente",
        rotulo = "Excelente!",
        corHex = Color(0xFF38BDF8),
        emocoes = listOf("Eufórico(a)", "Imparável", "Energizado(a)", "Orgulhoso(a)", "Forte", "Motivado(a)", "Confiante")
    ),
    NivelHumorPosTreino(
        id = "muito_bem",
        rotulo = "Muito bem",
        corHex = Color(0xFF22C55E),
        emocoes = listOf("Contente", "Animado(a)", "Satisfeito(a)", "Focado(a)", "Grato(a)", "Disposto(a)", "Revigorado(a)")
    ),
    NivelHumorPosTreino(
        id = "bem",
        rotulo = "Bem",
        corHex = Color(0xFFEAB308),
        emocoes = listOf("Calmo", "Contente", "Relaxado(a)", "Indiferente", "Aliviado", "Satisfeito", "Tranquilo")
    ),
    NivelHumorPosTreino(
        id = "ruim",
        rotulo = "Ruim",
        corHex = Color(0xFFF97316),
        emocoes = listOf("Cansado(a)", "Desgastado(a)", "Dolorido(a)", "Desmotivado(a)", "Estressado(a)", "Sem energia")
    ),
    NivelHumorPosTreino(
        id = "pessimo",
        rotulo = "Péssimo(a)",
        corHex = Color(0xFFEF4444),
        emocoes = listOf("Exausto(a)", "Esgotado(a)", "Sobrecarregado(a)", "Com dor", "Irritado(a)", "Desanimado(a)")
    )
)

@Composable
fun RostoHumorCanvas(
    humorId: String,
    cor: Color,
    tamanho: Dp = 46.dp
) {
    Canvas(modifier = Modifier.size(tamanho)) {
        val w = size.width
        val h = size.height

        // Olhos minimalistas
        drawCircle(
            color = cor,
            radius = w * 0.058f,
            center = Offset(w * 0.36f, h * 0.31f)
        )
        drawCircle(
            color = cor,
            radius = w * 0.058f,
            center = Offset(w * 0.64f, h * 0.31f)
        )

        // Boca expressiva orgânica
        val path = Path().apply {
            when (humorId) {
                "excelente" -> {
                    moveTo(w * 0.22f, h * 0.48f)
                    quadraticBezierTo(w * 0.50f, h * 0.54f, w * 0.78f, h * 0.48f)
                    quadraticBezierTo(w * 0.76f, h * 0.78f, w * 0.50f, h * 0.78f)
                    quadraticBezierTo(w * 0.24f, h * 0.78f, w * 0.22f, h * 0.48f)
                    close()
                }
                "muito_bem" -> {
                    moveTo(w * 0.22f, h * 0.50f)
                    quadraticBezierTo(w * 0.50f, h * 0.56f, w * 0.78f, h * 0.50f)
                    quadraticBezierTo(w * 0.75f, h * 0.73f, w * 0.50f, h * 0.73f)
                    quadraticBezierTo(w * 0.25f, h * 0.73f, w * 0.22f, h * 0.50f)
                    close()
                }
                "bem" -> {
                    moveTo(w * 0.20f, h * 0.54f)
                    quadraticBezierTo(w * 0.50f, h * 0.57f, w * 0.80f, h * 0.54f)
                    quadraticBezierTo(w * 0.78f, h * 0.69f, w * 0.50f, h * 0.69f)
                    quadraticBezierTo(w * 0.22f, h * 0.69f, w * 0.20f, h * 0.54f)
                    close()
                }
                "ruim" -> {
                    moveTo(w * 0.23f, h * 0.69f)
                    quadraticBezierTo(w * 0.50f, h * 0.48f, w * 0.77f, h * 0.69f)
                    quadraticBezierTo(w * 0.50f, h * 0.61f, w * 0.23f, h * 0.69f)
                    close()
                }
                else -> {
                    moveTo(w * 0.23f, h * 0.73f)
                    quadraticBezierTo(w * 0.50f, h * 0.44f, w * 0.77f, h * 0.73f)
                    quadraticBezierTo(w * 0.50f, h * 0.63f, w * 0.23f, h * 0.73f)
                    close()
                }
            }
        }
        drawPath(path = path, color = cor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoVerificacaoHumorPosTreino(
    checkinsNaSemana: Int = 1,
    diaSemanaAtualIdx: Int = 5, // 0=Dom .. 6=Sab
    aoConcluirVerificacao: (feelingFormatado: String) -> Unit,
    aoFechar: () -> Unit
) {
    var etapa by remember { mutableStateOf(1) }
    var humorSelecionadoId by remember { mutableStateOf("bem") }
    var emocoesSelecionadas by remember { mutableStateOf(listOf("Tranquilo")) }
    var emocoesExpandidas by remember { mutableStateOf(true) }
    var votoUtil by remember { mutableStateOf<String?>(null) }
    var exibirInfo by remember { mutableStateOf(false) }

    val humorAtual = remember(humorSelecionadoId) {
        NIVEIS_HUMOR_POS_TREINO.find { it.id == humorSelecionadoId } ?: NIVEIS_HUMOR_POS_TREINO[2]
    }

    val fundoRoxoEscuro = Color(0xFF140D2B)
    val cardRoxo = Color(0xFF231942)
    val botaoLilas = Color(0xFF7366E3)
    val chipInativo = Color(0xFF382D5E)

    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = fundoRoxoEscuro
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Barra Superior
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (etapa == 1) aoFechar()
                                    else etapa -= 1
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White
                                )
                            }
                            if (etapa < 3) {
                                Text(
                                    text = "Verificação de humor",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(onClick = { exibirInfo = !exibirInfo }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Informações",
                                tint = Color.White
                            )
                        }
                    }

                    if (exibirInfo) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardRoxo
                        ) {
                            Text(
                                text = "Registrar como você está se sentindo na conclusão do exercício/treino calibra sua recuperação muscular e o Coach IA.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ETAPA 1: Como você está se sentindo?
                    if (etapa == 1) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = cardRoxo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Como você está se sentindo?",
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                NIVEIS_HUMOR_POS_TREINO.forEachIndexed { idx, nivel ->
                                    val selecionado = humorSelecionadoId == nivel.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                humorSelecionadoId = nivel.id
                                                emocoesSelecionadas = listOf(nivel.emocoes.lastOrNull() ?: nivel.emocoes.first())
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(if (selecionado) botaoLilas else Color.Transparent)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (selecionado) botaoLilas else Color.White.copy(alpha = 0.45f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selecionado) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = fundoRoxoEscuro,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        RostoHumorCanvas(
                                            humorId = nivel.id,
                                            cor = nivel.corHex,
                                            tamanho = 44.dp
                                        )

                                        Text(
                                            text = nivel.rotulo,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    if (idx < NIVEIS_HUMOR_POS_TREINO.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Um humor é um estado mental geral e duradouro.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // ETAPA 2: Quais emoções descrevem melhor como você se sente?
                    if (etapa == 2) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = cardRoxo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Quais emoções descrevem melhor como você se sente?",
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    lineHeight = 30.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { emocoesExpandidas = !emocoesExpandidas }) {
                                        Icon(
                                            imageVector = if (emocoesExpandidas) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }

                                if (emocoesExpandidas) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        humorAtual.emocoes.forEach { emocao ->
                                            val ativa = emocoesSelecionadas.contains(emocao)
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (ativa) botaoLilas else chipInativo,
                                                modifier = Modifier.clickable {
                                                    emocoesSelecionadas = if (ativa) {
                                                        emocoesSelecionadas - emocao
                                                    } else {
                                                        emocoesSelecionadas + emocao
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = emocao,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Uma emoção é uma reação de curta duração, como alegria, raiva ou tristeza, a um evento ou experiência significativa.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // ETAPA 3: Card Resumo + Check-ins na Semana
                    if (etapa == 3) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = cardRoxo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RostoHumorCanvas(
                                    humorId = humorAtual.id,
                                    cor = humorAtual.corHex,
                                    tamanho = 120.dp
                                )

                                Text(
                                    text = humorAtual.rotulo,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Como você se sente?",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = emocoesSelecionadas.ifEmpty { listOf(humorAtual.emocoes.first()) }.joinToString(", "),
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.82f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = cardRoxo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                val qtdSem = checkinsNaSemana.coerceAtLeast(1)
                                Text(
                                    text = if (qtdSem == 1) "1 check-in realizado esta semana." else "$qtdSem check-ins realizados esta semana.",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                val letrasSemana = listOf("D", "S", "T", "Q", "Q", "S", "S")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    letrasSemana.forEachIndexed { idx, letra ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = letra,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (idx == 0) Color(0xFFF87171) else Color.White.copy(alpha = 0.7f)
                                            )
                                            if (idx == diaSemanaAtualIdx) {
                                                RostoHumorCanvas(
                                                    humorId = humorAtual.id,
                                                    cor = humorAtual.corHex,
                                                    tamanho = 26.dp
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.3f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Isto foi útil?",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF2A223A)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { votoUtil = "up" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = "Sim",
                                            tint = if (votoUtil == "up") botaoLilas else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { votoUtil = "down" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbDown,
                                            contentDescription = "Não",
                                            tint = if (votoUtil == "down") Color(0xFFF87171) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Botão Inferior ("Próximo" / "Concluir")
                Button(
                    onClick = {
                        if (etapa < 3) {
                            etapa += 1
                        } else {
                            val listaEmocoes = emocoesSelecionadas.ifEmpty { listOf(humorAtual.emocoes.first()) }
                            aoConcluirVerificacao("${humorAtual.rotulo} • ${listaEmocoes.joinToString(", ")}")
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = botaoLilas),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (etapa < 3) "Próximo" else "Concluir Check-in e Salvar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
