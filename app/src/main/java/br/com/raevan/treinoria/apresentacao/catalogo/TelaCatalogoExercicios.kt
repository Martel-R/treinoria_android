package br.com.raevan.treinoria.apresentacao.catalogo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.dados.remoto.dto.ExercicioDto
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.nucleo.tema.*

@Composable
fun TelaCatalogoExercicios() {
    val contexto = LocalContext.current
    var exercicios by remember { mutableStateOf<List<ExercicioDto>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var busca by remember { mutableStateOf("") }
    var grupoFiltro by remember { mutableStateOf("Todos") }

    LaunchedEffect(Unit) {
        carregando = true
        exercicios = RepositorioTreinoria.listarExercicios()
        carregando = false
    }

    val gruposMusculares = remember(exercicios) {
        listOf("Todos") + exercicios.map { it.muscleGroup }.distinct().sorted()
    }

    val filtrados = remember(exercicios, busca, grupoFiltro) {
        exercicios.filter { ex ->
            val matchGrupo = grupoFiltro == "Todos" || ex.muscleGroup.equals(grupoFiltro, ignoreCase = true)
            val matchBusca = busca.isBlank() ||
                ex.name.contains(busca, ignoreCase = true) ||
                ex.equipment.contains(busca, ignoreCase = true) ||
                ex.muscleGroup.contains(busca, ignoreCase = true)
            matchGrupo && matchBusca
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Catálogo Biomecânico (${filtrados.size} Exercícios)",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = VerdeEsmeralda
        )
        Text(
            text = "69 vídeos HD exclusivos + instruções técnicas de execução",
            fontSize = 12.sp,
            color = TextoSecundario
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            label = { Text("Buscar por exercício, músculo ou máquina...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(gruposMusculares) { grupo ->
                FilterChip(
                    selected = grupoFiltro == grupo,
                    onClick = { grupoFiltro = grupo },
                    label = { Text(grupo, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (carregando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VerdeEsmeralda)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filtrados, key = { it.id }) { ex ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuperficieCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ex.name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = TextoPrimario)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "💪 ${ex.muscleGroup}",
                                            fontSize = 11.sp,
                                            color = VerdeEsmeraldaClaro,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "⚙️ ${ex.equipment}",
                                            fontSize = 11.sp,
                                            color = CianoNeon
                                        )
                                    }
                                }

                                if (!ex.videoUrl.isNullOrBlank()) {
                                    FilledTonalButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ex.videoUrl))
                                                contexto.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Vídeo MP4", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (!ex.instructions.isNullOrBlank()) {
                                Text(
                                    text = ex.instructions,
                                    fontSize = 12.sp,
                                    color = TextoSecundario
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
