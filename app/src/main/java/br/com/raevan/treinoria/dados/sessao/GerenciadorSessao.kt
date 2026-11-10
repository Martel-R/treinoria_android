package br.com.raevan.treinoria.dados.sessao

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

class GerenciadorSessao private constructor(context: Context) : SessionManager {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveSession(session: UserSession) {
        val jsonStr = json.encodeToString(UserSession.serializer(), session)
        prefs.edit().putString(KEY_SESSION, jsonStr).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val jsonStr = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            json.decodeFromString(UserSession.serializer(), jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun temSessaoSalva(): Boolean {
        val sessionStr = prefs.getString(KEY_SESSION, null)
        return !sessionStr.isNullOrBlank()
    }

    fun salvarUltimoEmail(email: String) {
        prefs.edit().putString(KEY_ULTIMO_EMAIL, email.trim()).apply()
    }

    fun obterUltimoEmail(): String {
        return prefs.getString(KEY_ULTIMO_EMAIL, "") ?: ""
    }

    fun salvarPerfilLocal(userId: String, nome: String, role: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, nome)
            .putString(KEY_ACCOUNT_ROLE, role)
            .apply()
    }

    fun obterRoleSalva(): String {
        return prefs.getString(KEY_ACCOUNT_ROLE, "athlete") ?: "athlete"
    }

    fun obterNomeSalvo(): String {
        return prefs.getString(KEY_USER_NAME, "Atleta") ?: "Atleta"
    }

    companion object {
        private const val PREFS_NAME = "treinoria_sessao_prefs"
        private const val KEY_SESSION = "supabase_user_session"
        private const val KEY_ULTIMO_EMAIL = "ultimo_email_logado"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ACCOUNT_ROLE = "account_role"

        @Volatile
        private var INSTANCIA: GerenciadorSessao? = null

        fun inicializar(context: Context): GerenciadorSessao {
            return INSTANCIA ?: synchronized(this) {
                INSTANCIA ?: GerenciadorSessao(context.applicationContext).also { INSTANCIA = it }
            }
        }

        fun obterInstancia(): GerenciadorSessao {
            return INSTANCIA ?: throw IllegalStateException("GerenciadorSessao não foi inicializado!")
        }
    }
}
