package br.com.raevan.treinoria.nucleo.tema

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val VerdeEsmeralda = Color(0xFF10B981)
val VerdeEsmeraldaClaro = Color(0xFF34D399)
val VerdeEsmeraldaEscuro = Color(0xFF059669)
val CianoNeon = Color(0xFF06B6D4)
val AmbarDestaque = Color(0xFFF59E0B)
val RoxoDestaque = Color(0xFF8B5CF6)
val VermelhoAlerta = Color(0xFFF43F5E)

val FundoAcademia = Color(0xFF090D16)
val SuperficieCard = Color(0xFF111827)
val SuperficieElevada = Color(0xFF1E293B)
val BordaCard = Color(0xFF334155)
val TextoPrimario = Color(0xFFF8FAFC)
val TextoSecundario = Color(0xFF94A3B8)

private val EsquemaCoresTreinoria = darkColorScheme(
    primary = VerdeEsmeralda,
    onPrimary = Color(0xFF04130D),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = VerdeEsmeraldaClaro,
    secondary = CianoNeon,
    onSecondary = Color(0xFF041318),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFF67E8F9),
    tertiary = AmbarDestaque,
    background = FundoAcademia,
    onBackground = TextoPrimario,
    surface = SuperficieCard,
    onSurface = TextoPrimario,
    surfaceVariant = SuperficieElevada,
    onSurfaceVariant = TextoSecundario,
    outline = BordaCard,
    error = VermelhoAlerta
)

@Composable
fun TemaTreinoria(
    conteudo: @Composable () -> Unit
) {
    val visualizacao = LocalView.current
    if (!visualizacao.isInEditMode) {
        SideEffect {
            val janela = (visualizacao.context as? Activity)?.window
            if (janela != null) {
                janela.statusBarColor = FundoAcademia.toArgb()
                janela.navigationBarColor = FundoAcademia.toArgb()
                WindowCompat.getInsetsController(janela, visualizacao).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(janela, visualizacao).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = EsquemaCoresTreinoria,
        content = conteudo
    )
}
