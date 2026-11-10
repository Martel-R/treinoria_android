package br.com.raevan.treinoria.nucleo.componentes

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.raevan.treinoria.nucleo.tema.*

/**
 * Player de Vídeo MP4 Nativo que roda 100% dentro do próprio App Android
 * (em loop automático e sem abrir navegador externo).
 */
@Composable
fun PlayerVideoNativoCard(
    tituloExercicio: String,
    grupoMuscular: String,
    videoUrl: String,
    instrucoes: String? = null,
    aoFechar: () -> Unit
) {
    var carregandoVideo by remember { mutableStateOf(true) }
    var erroVideo by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FundoAcademia,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuperficieElevada)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = VerdeEsmeralda,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Execução: $tituloExercicio",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = VerdeEsmeraldaClaro
                    )
                }
                IconButton(
                    onClick = aoFechar,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar vídeo",
                        tint = TextoSecundario,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val videoView = VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.parse(videoUrl))
                                setOnPreparedListener { mp ->
                                    carregandoVideo = false
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    carregandoVideo = false
                                    erroVideo = true
                                    true
                                }
                            }
                            addView(videoView)
                        }
                    }
                )

                if (carregandoVideo && !erroVideo) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = VerdeEsmeralda,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Carregando vídeo de execução...",
                            fontSize = 12.sp,
                            color = TextoSecundario
                        )
                    }
                }

                if (erroVideo) {
                    Text(
                        text = "Não foi possível reproduzir este vídeo no momento.",
                        fontSize = 12.sp,
                        color = VermelhoAlerta,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (!instrucoes.isNullOrBlank()) {
                Text(
                    text = "💡 $instrucoes",
                    fontSize = 12.sp,
                    color = TextoPrimario,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SuperficieElevada.copy(alpha = 0.7f))
                        .padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun DialogoVideoExercicioNativo(
    tituloExercicio: String,
    grupoMuscular: String,
    videoUrl: String,
    instrucoes: String? = null,
    aoFechar: () -> Unit
) {
    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SuperficieCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    PlayerVideoNativoCard(
                        tituloExercicio = tituloExercicio,
                        grupoMuscular = grupoMuscular,
                        videoUrl = videoUrl,
                        instrucoes = instrucoes,
                        aoFechar = aoFechar
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = aoFechar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Fechar Demonstração", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
