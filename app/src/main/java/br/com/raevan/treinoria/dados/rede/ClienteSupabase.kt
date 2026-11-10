package br.com.raevan.treinoria.dados.rede

import android.content.Context
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.UserAgent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

object ClienteSupabase {
    const val SUPABASE_URL = "https://api-supabase.martel.page"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
    const val WEB_BASE_URL = "https://treinoria.raevan.com.br"
    const val USER_AGENT_PADRAO = "Mozilla/5.0 (Linux; Android 14; TreinorIA/1.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    @OptIn(ExperimentalSerializationApi::class)
    val jsonPadrao = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile
    private var clienteInstancia: SupabaseClient? = null

    @OptIn(SupabaseInternal::class)
    fun inicializar(context: Context) {
        if (clienteInstancia == null) {
            synchronized(this) {
                if (clienteInstancia == null) {
                    val sessionManager = GerenciadorSessao.inicializar(context.applicationContext)
                    clienteInstancia = createSupabaseClient(
                        supabaseUrl = SUPABASE_URL,
                        supabaseKey = SUPABASE_ANON_KEY
                    ) {
                        requestTimeout = 20.seconds
                        defaultSerializer = KotlinXSerializer(jsonPadrao)
                        httpConfig {
                            install(UserAgent) {
                                agent = USER_AGENT_PADRAO
                            }
                        }
                        install(Postgrest) {
                            defaultSchema = "gym"
                        }
                        install(Auth) {
                            this.sessionManager = sessionManager
                            this.codeVerifierCache = MemoryCodeVerifierCache()
                        }
                        install(Storage)
                    }
                }
            }
        }
    }

    val client: SupabaseClient
        get() = clienteInstancia ?: synchronized(this) {
            clienteInstancia ?: criarPadrao().also { clienteInstancia = it }
        }

    @OptIn(SupabaseInternal::class)
    private fun criarPadrao(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            requestTimeout = 20.seconds
            defaultSerializer = KotlinXSerializer(jsonPadrao)
            httpConfig {
                install(UserAgent) {
                    agent = USER_AGENT_PADRAO
                }
            }
            install(Postgrest) {
                defaultSchema = "gym"
            }
            install(Auth) {
                this.codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Storage)
        }
    }
}
