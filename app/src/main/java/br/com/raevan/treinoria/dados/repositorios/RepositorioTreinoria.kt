package br.com.raevan.treinoria.dados.repositorios

import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.remoto.dto.*
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime

object RepositorioTreinoria {

    fun obterUserIdAtual(): String? {
        return try {
            ClienteSupabase.client.auth.currentUserOrNull()?.id
                ?: ClienteSupabase.client.auth.currentSessionOrNull()?.user?.id
        } catch (_: Exception) {
            null
        }
    }

    suspend fun carregarPerfil(): PerfilDto? = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext null
        try {
            val lista = ClienteSupabase.client.from("profiles")
                .select {
                    filter { eq("id", uid) }
                }
                .decodeList<PerfilDto>()
            val perfil = lista.firstOrNull()
            if (perfil != null) {
                try {
                    GerenciadorSessao.obterInstancia()
                        .salvarPerfilLocal(perfil.id, perfil.name, perfil.accountRole)
                } catch (_: Exception) {}
            }
            perfil
        } catch (_: Exception) {
            null
        }
    }

    suspend fun garantirDadosIniciaisUsuario(): Unit = withContext(Dispatchers.IO) {
        try {
            ClienteSupabase.client.postgrest.rpc("seed_default_user_workouts")
        } catch (_: Exception) {}
    }

    suspend fun listarExercicios(): List<ExercicioDto> = withContext(Dispatchers.IO) {
        try {
            ClienteSupabase.client.from("exercises")
                .select {
                    order("muscle_group", Order.ASCENDING)
                    order("name", Order.ASCENDING)
                }
                .decodeList<ExercicioDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun listarFichasTreino(targetUserId: String? = null): List<FichaTreinoDto> = withContext(Dispatchers.IO) {
        val uid = targetUserId ?: obterUserIdAtual() ?: return@withContext emptyList()
        try {
            val cols = Columns.raw("*, template_exercises(*, exercise:exercises(*))")
            val fichas = ClienteSupabase.client.from("workout_templates")
                .select(columns = cols) {
                    filter { eq("user_id", uid) }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<FichaTreinoDto>()

            fichas.map { f ->
                f.copy(templateExercises = f.templateExercises.sortedBy { it.sortOrder })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun criarFichaComExercicios(
        targetUserId: String,
        titulo: String,
        subtitulo: String,
        diaSemana: String,
        corHex: String = "#10b981",
        exerciciosSelecionados: List<Triple<String, Int, String>> // (exerciseId, sets, reps)
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val novaFicha = buildJsonObject {
                put("user_id", targetUserId)
                put("title", titulo.trim())
                put("subtitle", subtitulo.trim())
                put("day_of_week", diaSemana)
                put("accent_color", corHex)
                put("sort_order", 1)
            }
            val inserida = ClienteSupabase.client.from("workout_templates")
                .insert(novaFicha) { select() }
                .decodeSingle<FichaTreinoDto>()

            exerciciosSelecionados.forEachIndexed { idx, item ->
                val vinc = buildJsonObject {
                    put("template_id", inserida.id)
                    put("exercise_id", item.first)
                    put("sort_order", idx + 1)
                    put("target_sets", item.second)
                    put("target_reps", item.third)
                    put("target_weight_kg", 20.0)
                    put("rest_seconds", 90)
                }
                ClienteSupabase.client.from("template_exercises").insert(vinc)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun excluirFicha(templateId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ClienteSupabase.client.from("workout_templates").delete {
                filter { eq("id", templateId) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listarSessoesRecentes(limite: Int = 25): List<SessaoTreinoDto> = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext emptyList()
        try {
            ClienteSupabase.client.from("workout_sessions")
                .select {
                    filter { eq("user_id", uid) }
                    order("started_at", Order.DESCENDING)
                    limit(limite.toLong())
                }
                .decodeList<SessaoTreinoDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun salvarSessaoTreinoConcluida(
        templateId: String?,
        titulo: String,
        duracaoSegundos: Int,
        feeling: String,
        notas: String,
        seriesConcluidas: List<SerieSessaoDto>
    ): SessaoTreinoDto? = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext null
        try {
            val volumeTotal = seriesConcluidas.sumOf { it.weightKg * it.reps }
            val totalSeries = seriesConcluidas.size
            val agora = OffsetDateTime.now().toString()

            val bodySessao = buildJsonObject {
                put("user_id", uid)
                if (!templateId.isNullOrBlank()) put("template_id", templateId)
                put("title", titulo)
                put("started_at", agora)
                put("finished_at", agora)
                put("duration_seconds", duracaoSegundos)
                put("total_volume_kg", volumeTotal)
                put("total_sets", totalSeries)
                put("feeling", feeling)
                if (notas.isNotBlank()) put("notes", notas)
            }

            val sessaoCriada = ClienteSupabase.client.from("workout_sessions")
                .insert(bodySessao) { select() }
                .decodeSingle<SessaoTreinoDto>()

            if (seriesConcluidas.isNotEmpty()) {
                val payloadSeries = seriesConcluidas.map { s ->
                    buildJsonObject {
                        put("session_id", sessaoCriada.id)
                        put("exercise_id", s.exerciseId)
                        put("set_number", s.setNumber)
                        put("weight_kg", s.weightKg)
                        put("reps", s.reps)
                        put("is_warmup", s.isWarmup)
                        put("completed", true)
                    }
                }
                ClienteSupabase.client.from("session_sets").insert(payloadSeries)
            }

            sessaoCriada
        } catch (_: Exception) {
            null
        }
    }

    suspend fun obterHabitoHoje(): HabitoDiarioDto = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext HabitoDiarioDto()
        val hoje = LocalDate.now().toString()
        try {
            val lista = ClienteSupabase.client.from("daily_habits")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("log_date", hoje)
                    }
                }
                .decodeList<HabitoDiarioDto>()
            lista.firstOrNull() ?: HabitoDiarioDto(userId = uid, logDate = hoje)
        } catch (_: Exception) {
            HabitoDiarioDto(userId = uid, logDate = hoje)
        }
    }

    suspend fun atualizarHabitoHoje(waterMl: Int, creatineTaken: Boolean): HabitoDiarioDto = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext HabitoDiarioDto()
        val hoje = LocalDate.now().toString()
        try {
            val payload = buildJsonObject {
                put("user_id", uid)
                put("log_date", hoje)
                put("water_ml", waterMl.coerceAtLeast(0))
                put("creatine_taken", creatineTaken)
            }
            ClienteSupabase.client.from("daily_habits")
                .upsert(payload) {
                    onConflict = "user_id,log_date"
                    select()
                }
                .decodeSingle<HabitoDiarioDto>()
        } catch (_: Exception) {
            HabitoDiarioDto(userId = uid, logDate = hoje, waterMl = waterMl, creatineTaken = creatineTaken)
        }
    }

    suspend fun listarRefeicoesRecentes(): List<RefeicaoLogDto> = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext emptyList()
        try {
            ClienteSupabase.client.from("meal_logs")
                .select {
                    filter { eq("user_id", uid) }
                    order("logged_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<RefeicaoLogDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun registrarRefeicao(
        titulo: String,
        calorias: Int,
        proteinaG: Double,
        carboG: Double,
        gorduraG: Double,
        feedbackIa: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext false
        try {
            val payload = buildJsonObject {
                put("user_id", uid)
                put("meal_title", titulo)
                put("calories", calorias)
                put("protein_g", proteinaG)
                put("carbs_g", carboG)
                put("fat_g", gorduraG)
                put("ai_feedback", feedbackIa)
            }
            ClienteSupabase.client.from("meal_logs").insert(payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listarMedidasCorporais(): List<MedidaCorporalDto> = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext emptyList()
        try {
            ClienteSupabase.client.from("body_measurements")
                .select {
                    filter { eq("user_id", uid) }
                    order("measured_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<MedidaCorporalDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun salvarMedidaCorporal(
        pesoKg: Double?,
        bfPct: Double?,
        peitoCm: Double?,
        cinturaCm: Double?,
        bracoCm: Double?,
        coxaCm: Double?,
        notas: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext false
        try {
            val payload = buildJsonObject {
                put("user_id", uid)
                put("measured_at", LocalDate.now().toString())
                if (pesoKg != null) put("weight_kg", pesoKg)
                if (bfPct != null) put("body_fat_pct", bfPct)
                if (peitoCm != null) put("chest_cm", peitoCm)
                if (cinturaCm != null) put("waist_cm", cinturaCm)
                if (bracoCm != null) put("arm_cm", bracoCm)
                if (coxaCm != null) put("thigh_cm", coxaCm)
                if (notas.isNotBlank()) put("notes", notas)
            }
            ClienteSupabase.client.from("body_measurements").insert(payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listarAlunosPersonal(): List<VinculoAlunoPersonalDto> = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext emptyList()
        try {
            ClienteSupabase.client.from("trainer_students")
                .select {
                    filter { eq("trainer_id", uid) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<VinculoAlunoPersonalDto>()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun cadastrarAlunoPersonal(
        nome: String,
        email: String,
        telefone: String,
        objetivo: String,
        notas: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext false
        try {
            val payload = buildJsonObject {
                put("trainer_id", uid)
                put("student_name", nome.trim())
                if (email.isNotBlank()) put("student_email", email.trim())
                if (telefone.isNotBlank()) put("student_phone", telefone.trim())
                put("goal", objetivo.trim().ifBlank { "Hipertrofia" })
                put("status", "active")
                if (notas.isNotBlank()) put("notes", notas.trim())
            }
            ClienteSupabase.client.from("trainer_students").insert(payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun consultarCoachIa(promptUsuario: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("${ClienteSupabase.WEB_BASE_URL}/api/ai/coach")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ClienteSupabase.USER_AGENT_PADRAO)
                connectTimeout = 20000
                readTimeout = 35000
                doOutput = true
            }
            val body = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(
                            JSONObject().put(
                                "text",
                                "Você é o Coach IA Oficial do TreinorIA PRO (Raevan Tech Group), especialista em hipertrofia, biomecânica e nutrição esportiva. Responda em português-BR de forma direta, técnica e motivadora:\n\n$promptUsuario"
                            )
                        ))
                    }
                ))
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val respTxt = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respTxt)
                val cand = json.optJSONArray("candidates")?.optJSONObject(0)
                val text = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) return@withContext text.trim()
            }
            "Mantenha a cadência controlada (3s na fase excêntrica, 1s de contração máxima) e progrida 2,5 kg sempre que atingir o topo da faixa de repetições com RPE <= 8.5!"
        } catch (_: Exception) {
            "Dica do Coach IA: Controle a fase excêntrica em 3 segundos, mantenha hidratação acima de 35ml/kg e busque falha técnica próxima de 1-2 repetições na reserva (RIR)."
        }
    }

    suspend fun enviarMensagemWhatsapp(numero: String, texto: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val digitos = numero.replace(Regex("\\D"), "")
            if (digitos.length < 10) return@withContext false
            val destino = if (digitos.startsWith("55")) digitos else "55$digitos"

            val url = URL("${ClienteSupabase.WEB_BASE_URL}/api/whatsapp/send")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ClienteSupabase.USER_AGENT_PADRAO)
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
            }
            val body = JSONObject().apply {
                put("number", destino)
                put("text", texto)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
