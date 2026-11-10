package br.com.raevan.treinoria.nucleo.atualizacao

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import br.com.raevan.treinoria.nucleo.tema.CianoNeon
import br.com.raevan.treinoria.nucleo.tema.VerdeEsmeralda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class InfoVersaoRemota(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val fallbackApkUrl: String,
    val releaseNotes: String,
    val publishedAt: String
)

object GerenciadorAtualizacaoApp {
    private val URLS_MANIFESTO = listOf(
        "https://treinoria.raevan.com.br/apk/version.json",
        "https://github.com/Martel-R/treinoria_android/releases/download/latest-apk/version.json"
    )

    fun obterVersaoInstalada(context: Context): Pair<Int, String> {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
            val name = pkgInfo.versionName ?: "1.0.0"
            code to name
        } catch (_: Throwable) {
            1 to "1.0.0"
        }
    }

    private fun abrirConexaoSeguindoRedirects(urlInicial: String, timeoutMs: Int = 15000): HttpURLConnection {
        var urlAtual = urlInicial
        var tentativas = 0
        while (tentativas < 6) {
            val conn = (URL(urlAtual).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("User-Agent", "TreinorIA-Android-Updater/1.0")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val status = conn.responseCode
            if (status in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val novaLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (novaLocation.isNullOrBlank()) break
                urlAtual = novaLocation
                tentativas++
            } else {
                return conn
            }
        }
        return URL(urlAtual).openConnection() as HttpURLConnection
    }

    suspend fun consultarVersaoRemota(): InfoVersaoRemota? = withContext(Dispatchers.IO) {
        val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
        for (urlBase in URLS_MANIFESTO) {
            try {
                val urlComTs = "$urlBase?t=${System.currentTimeMillis()}"
                val conn = abrirConexaoSeguindoRedirects(urlComTs, 10000)
                if (conn.responseCode in 200..299) {
                    val texto = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = jsonParser.parseToJsonElement(texto).jsonObject
                    val code = obj["versionCode"]?.jsonPrimitive?.intOrNull ?: continue
                    val name = obj["versionName"]?.jsonPrimitive?.content ?: "1.0.$code"
                    val apkUrl = obj["apkUrl"]?.jsonPrimitive?.content
                        ?: "https://treinoria.raevan.com.br/apk/treinoria.apk"
                    val fallbackUrl = obj["fallbackApkUrl"]?.jsonPrimitive?.content
                        ?: "https://github.com/Martel-R/treinoria_android/releases/download/latest-apk/treinoria.apk"
                    val notes = obj["releaseNotes"]?.jsonPrimitive?.content ?: "Melhorias de performance e novos recursos."
                    val pub = obj["publishedAt"]?.jsonPrimitive?.content ?: ""
                    return@withContext InfoVersaoRemota(
                        versionCode = code,
                        versionName = name,
                        apkUrl = apkUrl,
                        fallbackApkUrl = fallbackUrl,
                        releaseNotes = notes,
                        publishedAt = pub
                    )
                }
            } catch (_: Throwable) {}
        }
        null
    }

    suspend fun verificarAtualizacaoDisponivel(context: Context): InfoVersaoRemota? {
        val (instaladoCode, _) = obterVersaoInstalada(context)
        val remoto = consultarVersaoRemota() ?: return null
        return if (remoto.versionCode > instaladoCode) remoto else null
    }

    suspend fun baixarApkAtualizacao(
        context: Context,
        info: InfoVersaoRemota,
        aoProgresso: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val pastaUpdate = File(context.cacheDir, "atualizacao").apply { mkdirs() }
        val arquivoDestino = File(pastaUpdate, "treinoria-v${info.versionCode}.apk")
        if (arquivoDestino.exists()) arquivoDestino.delete()

        val urlsDownload = listOf(info.apkUrl, info.fallbackApkUrl).distinct()
        var ultimoErro = "Falha no download do APK"

        for (urlDownload in urlsDownload) {
            try {
                val conn = abrirConexaoSeguindoRedirects("$urlDownload?t=${System.currentTimeMillis()}", 30000)
                if (conn.responseCode !in 200..299) {
                    ultimoErro = "HTTP ${conn.responseCode}"
                    continue
                }
                val tamanhoTotal = conn.contentLengthLong.takeIf { it > 0 } ?: 12_000_000L
                var bytesBaixados = 0L

                conn.inputStream.use { entrada ->
                    FileOutputStream(arquivoDestino).use { saida ->
                        val buffer = ByteArray(32 * 1024)
                        var lidos: Int
                        while (entrada.read(buffer).also { lidos = it } != -1) {
                            saida.write(buffer, 0, lidos)
                            bytesBaixados += lidos
                            val pct = ((bytesBaixados * 100L) / tamanhoTotal).toInt().coerceIn(1, 99)
                            withContext(Dispatchers.Main) { aoProgresso(pct) }
                        }
                        saida.flush()
                    }
                }

                if (arquivoDestino.exists() && arquivoDestino.length() > 3_000_000L) {
                    val cabecalho = ByteArray(2)
                    arquivoDestino.inputStream().use { it.read(cabecalho) }
                    if (cabecalho[0] == 0x50.toByte() && cabecalho[1] == 0x4B.toByte()) {
                        withContext(Dispatchers.Main) { aoProgresso(100) }
                        return@withContext arquivoDestino
                    }
                }
                ultimoErro = "Arquivo APK incompleto"
            } catch (e: Throwable) {
                ultimoErro = e.message ?: "Erro de rede"
            }
        }
        throw IllegalStateException(ultimoErro)
    }

    fun podeInstalarPacotes(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { context.packageManager.canRequestPackageInstalls() } catch (_: Throwable) { true }
        } else true
    }

    fun abrirConfiguracaoFontesDesconhecidas(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }

    fun iniciarInstalacaoApk(context: Context, arquivoApk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivoApk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Composable
fun DialogoAtualizacaoApp(
    infoRemota: InfoVersaoRemota,
    versaoInstaladaNome: String,
    versaoInstaladaCodigo: Int,
    aoFechar: () -> Unit
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()

    var baixando by remember { mutableStateOf(false) }
    var progresso by remember { mutableStateOf(0) }
    var arquivoBaixado by remember { mutableStateOf<File?>(null) }
    var aguardandoPermissao by remember { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!baixando) aoFechar() },
        icon = {
            Icon(
                imageVector = if (arquivoBaixado != null) Icons.Default.CheckCircle else Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = if (arquivoBaixado != null) VerdeEsmeralda else CianoNeon,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = if (arquivoBaixado != null) "Atualização Pronta!" else "Nova Versão do TreinorIA",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Uma nova versão do aplicativo TreinorIA (Raevan Tech Group) está disponível:",
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VerdeEsmeralda.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Instalada: v$versaoInstaladaNome (#$versaoInstaladaCodigo)", fontSize = 12.sp)
                        Text(
                            "Disponível: v${infoRemota.versionName} (#${infoRemota.versionCode})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerdeEsmeralda
                        )
                        if (infoRemota.releaseNotes.isNotBlank()) {
                            Text("Novidades: ${infoRemota.releaseNotes}", fontSize = 12.sp)
                        }
                    }
                }
                if (baixando) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Baixando APK...", fontSize = 12.sp)
                            Text("$progresso%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeEsmeralda)
                        }
                        LinearProgressIndicator(
                            progress = { progresso / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = VerdeEsmeralda
                        )
                    }
                }
                if (aguardandoPermissao) {
                    Text(
                        text = "Autorize \"Permitir desta fonte\" nas configurações e toque em \"Instalar Agora\".",
                        fontSize = 12.sp,
                        color = VerdeEsmeralda
                    )
                }
                if (mensagemErro != null) {
                    Text("Erro: $mensagemErro", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !baixando,
                onClick = {
                    val arq = arquivoBaixado
                    if (arq != null && arq.exists()) {
                        if (!GerenciadorAtualizacaoApp.podeInstalarPacotes(contexto)) {
                            aguardandoPermissao = true
                            GerenciadorAtualizacaoApp.abrirConfiguracaoFontesDesconhecidas(contexto)
                        } else {
                            GerenciadorAtualizacaoApp.iniciarInstalacaoApk(contexto, arq)
                        }
                    } else {
                        baixando = true
                        progresso = 1
                        mensagemErro = null
                        escopo.launch {
                            try {
                                val baixado = GerenciadorAtualizacaoApp.baixarApkAtualizacao(contexto, infoRemota) {
                                    progresso = it
                                }
                                baixando = false
                                arquivoBaixado = baixado
                                if (!GerenciadorAtualizacaoApp.podeInstalarPacotes(contexto)) {
                                    aguardandoPermissao = true
                                    GerenciadorAtualizacaoApp.abrirConfiguracaoFontesDesconhecidas(contexto)
                                } else {
                                    GerenciadorAtualizacaoApp.iniciarInstalacaoApk(contexto, baixado)
                                }
                            } catch (e: Throwable) {
                                baixando = false
                                mensagemErro = e.message ?: "Falha ao baixar APK"
                            }
                        }
                    }
                }
            ) {
                Text(if (baixando) "Baixando..." else if (arquivoBaixado != null) "Instalar Agora" else "Atualizar Agora")
            }
        },
        dismissButton = {
            if (!baixando) {
                TextButton(onClick = aoFechar) { Text("Depois") }
            }
        }
    )
}
