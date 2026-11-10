package br.com.raevan.treinoria.nucleo.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.repositorios.StatusMuscularInfo
import br.com.raevan.treinoria.nucleo.tema.*

val MUSCULOS_RASTREADOS = listOf(
    "Peito",
    "Costas",
    "Ombros",
    "Bíceps",
    "Tríceps",
    "Abdômen",
    "Pernas",
    "Glúteos"
)

private fun corDoStatus(status: String?): Color {
    return when (status) {
        "fatigued" -> VermelhoAlerta
        "recovering" -> AmbarDestaque
        else -> VerdeEsmeralda
    }
}

/**
 * Mapa Corporal 2D (Frente & Costas) de Fadiga & Recuperação Muscular +
 * Barras de Volume Semanal (7 dias) por grupo muscular — idêntico ao Web App!
 */
@Composable
fun MapaCorporalFadigaCard(
    mapaStatus: Map<String, StatusMuscularInfo>
) {
    val corOmbros = corDoStatus(mapaStatus["Ombros"]?.status)
    val corPeito = corDoStatus(mapaStatus["Peito"]?.status)
    val corBiceps = corDoStatus(mapaStatus["Bíceps"]?.status)
    val corAbdomen = corDoStatus(mapaStatus["Abdômen"]?.status)
    val corPernas = corDoStatus(mapaStatus["Pernas"]?.status)
    val corCostas = corDoStatus(mapaStatus["Costas"]?.status)
    val corTriceps = corDoStatus(mapaStatus["Tríceps"]?.status)
    val corGluteos = corDoStatus(mapaStatus["Glúteos"]?.status)

    val temMusculoFadigado = mapaStatus.values.any { it.status == "fatigued" }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SuperficieCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho + Legenda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = VerdeEsmeralda,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mapa Corporal de Fadiga & Recuperação",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = TextoPrimario
                        )
                    }
                    Text(
                        text = "Status biológico de cada músculo nas últimas 48h + meta semanal de séries",
                        fontSize = 11.sp,
                        color = TextoSecundario
                    )
                }
            }

            // Legenda de Cores (Pronto / 24-48h / Fadigado)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(VerdeEsmeralda))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("100% Pronto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VerdeEsmeralda)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(AmbarDestaque))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Quase Pronto (28-52h)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmbarDestaque)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(VermelhoAlerta))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Fadigado (<28h)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VermelhoAlerta)
                }
            }

            // Silhuetas Anatômicas 2D (FRENTE & COSTAS) lado a lado
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FundoAcademia,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SILHUETA FRENTE
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FRENTE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = TextoSecundario
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Canvas(modifier = Modifier.width(96.dp).height(176.dp)) {
                            val sx = size.width / 120f
                            val sy = size.height / 240f

                            // Cabeça
                            drawCircle(
                                color = Color(0xFF334155),
                                radius = 14f * sx,
                                center = Offset(60f * sx, 22f * sy)
                            )
                            // Ombros (Deltoides)
                            drawRoundRect(
                                color = corOmbros.copy(alpha = 0.9f),
                                topLeft = Offset(22f * sx, 40f * sy),
                                size = Size(18f * sx, 24f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            drawRoundRect(
                                color = corOmbros.copy(alpha = 0.9f),
                                topLeft = Offset(80f * sx, 40f * sy),
                                size = Size(18f * sx, 24f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            // Peitoral
                            drawRoundRect(
                                color = corPeito.copy(alpha = 0.95f),
                                topLeft = Offset(41f * sx, 42f * sy),
                                size = Size(38f * sx, 26f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            // Bíceps
                            drawRoundRect(
                                color = corBiceps.copy(alpha = 0.9f),
                                topLeft = Offset(18f * sx, 66f * sy),
                                size = Size(15f * sx, 34f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            drawRoundRect(
                                color = corBiceps.copy(alpha = 0.9f),
                                topLeft = Offset(87f * sx, 66f * sy),
                                size = Size(15f * sx, 34f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            // Abdômen / Core
                            drawRoundRect(
                                color = corAbdomen.copy(alpha = 0.9f),
                                topLeft = Offset(44f * sx, 70f * sy),
                                size = Size(32f * sx, 42f * sy),
                                cornerRadius = CornerRadius(5f * sx, 5f * sy)
                            )
                            // Quadríceps / Pernas
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.95f),
                                topLeft = Offset(38f * sx, 116f * sy),
                                size = Size(20f * sx, 64f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.95f),
                                topLeft = Offset(62f * sx, 116f * sy),
                                size = Size(20f * sx, 64f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            // Panturrilhas
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.75f),
                                topLeft = Offset(40f * sx, 184f * sy),
                                size = Size(16f * sx, 42f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.75f),
                                topLeft = Offset(64f * sx, 184f * sy),
                                size = Size(16f * sx, 42f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                        }
                    }

                    // SILHUETA COSTAS
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "COSTAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = TextoSecundario
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Canvas(modifier = Modifier.width(96.dp).height(176.dp)) {
                            val sx = size.width / 120f
                            val sy = size.height / 240f

                            // Cabeça
                            drawCircle(
                                color = Color(0xFF334155),
                                radius = 14f * sx,
                                center = Offset(60f * sx, 22f * sy)
                            )
                            // Ombros Posteriores
                            drawRoundRect(
                                color = corOmbros.copy(alpha = 0.9f),
                                topLeft = Offset(22f * sx, 40f * sy),
                                size = Size(18f * sx, 22f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            drawRoundRect(
                                color = corOmbros.copy(alpha = 0.9f),
                                topLeft = Offset(80f * sx, 40f * sy),
                                size = Size(18f * sx, 22f * sy),
                                cornerRadius = CornerRadius(8f * sx, 8f * sy)
                            )
                            // Dorsal / Costas (Polígono em V)
                            val pathCostas = Path().apply {
                                moveTo(40f * sx, 42f * sy)
                                lineTo(80f * sx, 42f * sy)
                                lineTo(74f * sx, 108f * sy)
                                lineTo(46f * sx, 108f * sy)
                                close()
                            }
                            drawPath(path = pathCostas, color = corCostas.copy(alpha = 0.95f))

                            // Tríceps
                            drawRoundRect(
                                color = corTriceps.copy(alpha = 0.9f),
                                topLeft = Offset(18f * sx, 64f * sy),
                                size = Size(15f * sx, 36f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            drawRoundRect(
                                color = corTriceps.copy(alpha = 0.9f),
                                topLeft = Offset(87f * sx, 64f * sy),
                                size = Size(15f * sx, 36f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            // Glúteos
                            drawRoundRect(
                                color = corGluteos.copy(alpha = 0.95f),
                                topLeft = Offset(39f * sx, 110f * sy),
                                size = Size(42f * sx, 28f * sy),
                                cornerRadius = CornerRadius(10f * sx, 10f * sy)
                            )
                            // Posteriores de Coxa
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.9f),
                                topLeft = Offset(39f * sx, 142f * sy),
                                size = Size(19f * sx, 44f * sy),
                                cornerRadius = CornerRadius(7f * sx, 7f * sy)
                            )
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.9f),
                                topLeft = Offset(62f * sx, 142f * sy),
                                size = Size(19f * sx, 44f * sy),
                                cornerRadius = CornerRadius(7f * sx, 7f * sy)
                            )
                            // Panturrilhas
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.75f),
                                topLeft = Offset(41f * sx, 188f * sy),
                                size = Size(15f * sx, 38f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                            drawRoundRect(
                                color = corPernas.copy(alpha = 0.75f),
                                topLeft = Offset(64f * sx, 188f * sy),
                                size = Size(15f * sx, 38f * sy),
                                cornerRadius = CornerRadius(6f * sx, 6f * sy)
                            )
                        }
                    }
                }
            }

            // Lista dos 8 Grupos Musculares com Status + Barra de Volume Semanal (Meta 12-16 séries em 7d)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MUSCULOS_RASTREADOS.forEach { musculo ->
                    val info = mapaStatus[musculo] ?: StatusMuscularInfo(
                        muscleGroup = musculo,
                        weeklySets = 0,
                        targetSets = if (musculo in listOf("Pernas", "Costas", "Peito")) 16 else 12,
                        status = "ready",
                        label = "100% Descansado"
                    )
                    val cor = corDoStatus(info.status)
                    val progresso = (info.weeklySets.toFloat() / info.targetSets.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuperficieElevada.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(cor)
                                    )
                                    Text(
                                        text = musculo,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = TextoPrimario
                                    )
                                    Text(
                                        text = info.label,
                                        fontSize = 10.sp,
                                        color = TextoSecundario
                                    )
                                }
                                Text(
                                    text = "${info.weeklySets}/${info.targetSets} séries (7d)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cor
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progresso },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = cor,
                                trackColor = FundoAcademia
                            )
                        }
                    }
                }
            }

            // Rodapé de Recomendação Biológica
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = FundoAcademia,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (temMusculoFadigado) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (temMusculoFadigado) AmbarDestaque else VerdeEsmeralda,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (temMusculoFadigado)
                            "Dica de Periodização: Priorize hoje os grupos em Verde (100% Prontos) para máxima hipertrofia."
                        else
                            "Todos os seus grupos musculares estão recuperados e prontos para progressão de carga!",
                        fontSize = 11.sp,
                        color = TextoPrimario
                    )
                }
            }
        }
    }
}
