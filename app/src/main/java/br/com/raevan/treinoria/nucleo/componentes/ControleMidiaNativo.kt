package br.com.raevan.treinoria.nucleo.componentes

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.raevan.treinoria.nucleo.tema.*
import kotlinx.coroutines.delay

/**
 * Envia comando de mídia nativo (Play/Pause, Faixa Anterior, Próxima Faixa)
 * diretamente para o player de música ativo no Android (Spotify, YouTube Music, Deezer, Apple Music, etc.)
 * sem sair da tela cheia de contagem!
 */
fun enviarComandoMidiaSistema(context: Context, keyCode: Int) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val agora = SystemClock.uptimeMillis()
        val eventoDown = KeyEvent(agora, agora, KeyEvent.ACTION_DOWN, keyCode, 0)
        val eventoUp = KeyEvent(agora, agora, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(eventoDown)
        audioManager.dispatchMediaKeyEvent(eventoUp)
    } catch (_: Throwable) {}
}

/**
 * Barra Minimalista de Controle de Mídia (Música Anterior / Play-Pause / Próxima Música)
 * para a Tela Cheia de Contagem de Repetições e Descanso.
 */
@Composable
fun BarraControleMidiaTelaCheia() {
    val contexto = LocalContext.current
    val audioManager = remember {
        contexto.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var tocandoMusica by remember {
        mutableStateOf(audioManager?.isMusicActive == true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            tocandoMusica = audioManager?.isMusicActive == true
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SuperficieCard.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CianoNeon.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = CianoNeon,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = if (tocandoMusica) "Música em Reprodução" else "Player de Música",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextoPrimario
                    )
                    Text(
                        text = "Spotify • YT Music • Sistema",
                        fontSize = 10.sp,
                        color = TextoSecundario
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Faixa Anterior (⏮)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SuperficieElevada)
                        .clickable {
                            enviarComandoMidiaSistema(contexto, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Música Anterior",
                        tint = TextoPrimario,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause (⏯)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CianoNeon)
                        .clickable {
                            enviarComandoMidiaSistema(contexto, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            tocandoMusica = !tocandoMusica
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tocandoMusica) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play ou Pause",
                        tint = FundoAcademia,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Próxima Faixa (⏭)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SuperficieElevada)
                        .clickable {
                            enviarComandoMidiaSistema(contexto, KeyEvent.KEYCODE_MEDIA_NEXT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Próxima Música",
                        tint = TextoPrimario,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
