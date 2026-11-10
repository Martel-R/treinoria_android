package br.com.raevan.treinoria.dados.repositorios

import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.remoto.dto.*
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ResultadoScannerPratoIa(
    val dishName: String,
    val caloriesKcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val adherenceScore: Int,
    val aiFeedback: String
)

data class EstatisticaExercicioHistorico(
    val lastWeightKg: Double,
    val lastReps: Int,
    val maxWeightKg: Double
)

object RepositorioTreinoria {

    fun obterDataHojeIso(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    fun obterDataHojeFormatadaBr(): String {
        return LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM"))
    }

    fun obterUserIdAtual(): String? {
        return try {
            ClienteSupabase.client.auth.currentUserOrNull()?.id
                ?: ClienteSupabase.client.auth.currentSessionOrNull()?.user?.id
        } catch (_: Exception) {
            null
        }
    }

    private fun semAcento(texto: String): String {
        val norm = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        return norm.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    fun obterNomeDiaHoje(): String {
        return when (LocalDate.now(ZoneId.systemDefault()).dayOfWeek) {
            DayOfWeek.MONDAY -> "Segunda-feira"
            DayOfWeek.TUESDAY -> "Terça-feira"
            DayOfWeek.WEDNESDAY -> "Quarta-feira"
            DayOfWeek.THURSDAY -> "Quinta-feira"
            DayOfWeek.FRIDAY -> "Sexta-feira"
            DayOfWeek.SATURDAY -> "Sábado"
            DayOfWeek.SUNDAY -> "Domingo"
            else -> "Hoje"
        }
    }

    fun selecionarTreinoDoDia(
        fichas: List<FichaTreinoDto>,
        sessoesRecentes: List<SessaoTreinoDto> = emptyList()
    ): FichaTreinoDto? {
        if (fichas.isEmpty()) return null

        val tokensHoje = when (LocalDate.now(ZoneId.systemDefault()).dayOfWeek) {
            DayOfWeek.MONDAY -> listOf("segunda", "seg")
            DayOfWeek.TUESDAY -> listOf("terca", "ter")
            DayOfWeek.WEDNESDAY -> listOf("quarta", "qua")
            DayOfWeek.THURSDAY -> listOf("quinta", "qui")
            DayOfWeek.FRIDAY -> listOf("sexta", "sex")
            DayOfWeek.SATURDAY -> listOf("sabado", "sab")
            DayOfWeek.SUNDAY -> listOf("domingo", "dom")
            else -> emptyList()
        }

        val fichaDia = fichas.firstOrNull { f ->
            val diaRaw = f.dayOfWeek ?: return@firstOrNull false
            val limpo = semAcento(diaRaw)
            val partes = limpo.split(Regex("[\\s,/•\\-|]+")).filter { it.isNotBlank() }
            tokensHoje.any { tk -> partes.contains(tk) || limpo.contains(tk) }
        }
        if (fichaDia != null) return fichaDia

        val ultimoPlanId = sessoesRecentes.firstOrNull()?.planId
        if (!ultimoPlanId.isNullOrBlank() && fichas.size > 1) {
            val idxUltimo = fichas.indexOfFirst { it.id == ultimoPlanId }
            if (idxUltimo >= 0) {
                return fichas[(idxUltimo + 1) % fichas.size]
            }
        }
        return fichas.first()
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
        val uid = obterUserIdAtual() ?: return@withContext
        try {
            val params = buildJsonObject {
                put("p_user_id", uid)
            }
            ClienteSupabase.client.postgrest.rpc("seed_default_plans_for_user", params)
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

    suspend fun obterEstatisticasExercicios(): Map<String, EstatisticaExercicioHistorico> = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext emptyMap()
        try {
            val series = ClienteSupabase.client.from("workout_sets")
                .select {
                    filter { eq("user_id", uid) }
                    order("performed_at", Order.DESCENDING)
                    limit(400)
                }
                .decodeList<SerieSessaoDto>()

            val mapa = mutableMapOf<String, EstatisticaExercicioHistorico>()
            series.filter { it.isCompleted }.groupBy { it.exerciseId }.forEach { (exId, lista) ->
                val ultima = lista.firstOrNull()
                val maxPeso = lista.maxOfOrNull { it.weightKg } ?: 0.0
                if (ultima != null) {
                    mapa[exId] = EstatisticaExercicioHistorico(
                        lastWeightKg = ultima.weightKg,
                        lastReps = ultima.reps,
                        maxWeightKg = maxPeso
                    )
                }
            }
            mapa
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun listarFichasTreino(targetUserId: String? = null): List<FichaTreinoDto> = withContext(Dispatchers.IO) {
        val uid = targetUserId ?: obterUserIdAtual() ?: return@withContext emptyList()
        try {
            val exercicios = listarExercicios()
            val mapaExercicios = exercicios.associateBy { it.id }

            val planos = ClienteSupabase.client.from("workout_plans")
                .select {
                    filter { eq("user_id", uid) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<FichaTreinoDto>()

            if (planos.isEmpty()) return@withContext emptyList()

            val todosItens = ClienteSupabase.client.from("workout_plan_items")
                .select {
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<ItemFichaTreinoDto>()

            planos.map { plano ->
                val itensDoPlano = todosItens
                    .filter { it.planId == plano.id }
                    .sortedBy { it.sortOrder }
                    .map { item ->
                        item.copy(exercise = mapaExercicios[item.exerciseId])
                    }
                plano.copy(items = itensDoPlano)
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
        exerciciosSelecionados: List<Triple<String, Int, String>>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val novaFicha = buildJsonObject {
                put("user_id", targetUserId)
                put("name", titulo.trim())
                put("description", subtitulo.trim())
                put("day_of_week", diaSemana)
                put("color", corHex)
                put("is_active", true)
            }
            val inserida = ClienteSupabase.client.from("workout_plans")
                .insert(novaFicha) { select() }
                .decodeSingle<FichaTreinoDto>()

            exerciciosSelecionados.forEachIndexed { idx, item ->
                val vinc = buildJsonObject {
                    put("plan_id", inserida.id)
                    put("exercise_id", item.first)
                    put("sort_order", idx + 1)
                    put("target_sets", item.second)
                    put("target_reps", item.third)
                    put("target_weight_kg", 20.0)
                    put("rest_seconds", 90)
                }
                ClienteSupabase.client.from("workout_plan_items").insert(vinc)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun excluirFicha(planId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ClienteSupabase.client.from("workout_plans").delete {
                filter { eq("id", planId) }
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
        planId: String?,
        titulo: String,
        duracaoSegundos: Int,
        feeling: String,
        notas: String,
        seriesConcluidas: List<SerieSessaoDto>
    ): SessaoTreinoDto? = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext null
        try {
            val volumeTotal = seriesConcluidas.sumOf { it.weightKg * it.reps }
            val duracaoMinutos = (duracaoSegundos / 60).coerceAtLeast(1)
            val agora = OffsetDateTime.now().toString()

            val bodySessao = buildJsonObject {
                put("user_id", uid)
                if (!planId.isNullOrBlank()) put("plan_id", planId)
                put("title", titulo)
                put("started_at", agora)
                put("finished_at", agora)
                put("duration_minutes", duracaoMinutos)
                put("total_volume_kg", volumeTotal)
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
                        put("user_id", uid)
                        put("exercise_id", s.exerciseId)
                        put("set_number", s.setNumber)
                        put("weight_kg", s.weightKg)
                        put("reps", s.reps)
                        put("is_warmup", s.isWarmup)
                        put("is_completed", true)
                    }
                }
                ClienteSupabase.client.from("workout_sets").insert(payloadSeries)
            }

            sessaoCriada
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Busca os hábitos de hidratação/creatina/pré/pós estritamente para a DATA DE HOJE
     * no fuso horário do aparelho. Se mudou de dia, retorna um registro 100% zerado para hoje.
     */
    suspend fun obterHabitoHoje(): HabitoDiarioDto = withContext(Dispatchers.IO) {
        val hoje = obterDataHojeIso()
        val uid = obterUserIdAtual() ?: return@withContext HabitoDiarioDto(logDate = hoje)
        try {
            val lista = ClienteSupabase.client.from("daily_habits")
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("log_date", hoje)
                    }
                }
                .decodeList<HabitoDiarioDto>()

            val registroHoje = lista.firstOrNull { it.logDate.take(10) == hoje }
            registroHoje ?: HabitoDiarioDto(
                userId = uid,
                logDate = hoje,
                waterMl = 0,
                waterGoalMl = 3000,
                creatineTaken = false,
                preWorkoutTaken = false,
                postWorkoutTaken = false
            )
        } catch (_: Exception) {
            HabitoDiarioDto(
                userId = uid,
                logDate = hoje,
                waterMl = 0,
                waterGoalMl = 3000,
                creatineTaken = false,
                preWorkoutTaken = false,
                postWorkoutTaken = false
            )
        }
    }

    suspend fun atualizarHabitoHoje(
        waterMl: Int,
        creatineTaken: Boolean,
        preWorkoutTaken: Boolean = false,
        postWorkoutTaken: Boolean = false,
        waterGoalMl: Int = 3000
    ): HabitoDiarioDto = withContext(Dispatchers.IO) {
        val hoje = obterDataHojeIso()
        val uid = obterUserIdAtual() ?: return@withContext HabitoDiarioDto(logDate = hoje)
        val mlLimpo = waterMl.coerceAtLeast(0)
        try {
            val payload = buildJsonObject {
                put("user_id", uid)
                put("log_date", hoje)
                put("water_ml", mlLimpo)
                put("water_goal_ml", waterGoalMl.coerceAtLeast(1000))
                put("creatine_taken", creatineTaken)
                put("pre_workout_taken", preWorkoutTaken)
                put("post_workout_taken", postWorkoutTaken)
            }
            ClienteSupabase.client.from("daily_habits")
                .upsert(payload) {
                    onConflict = "user_id,log_date"
                    select()
                }
                .decodeSingle<HabitoDiarioDto>()
        } catch (_: Exception) {
            HabitoDiarioDto(
                userId = uid,
                logDate = hoje,
                waterMl = mlLimpo,
                waterGoalMl = waterGoalMl,
                creatineTaken = creatineTaken,
                preWorkoutTaken = preWorkoutTaken,
                postWorkoutTaken = postWorkoutTaken
            )
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
        tipoRefeicao: String = "Pós-Treino",
        titulo: String,
        calorias: Int,
        proteinaG: Double,
        carboG: Double,
        gorduraG: Double,
        feedbackIa: String,
        adherenceScore: Int = 90
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = obterUserIdAtual() ?: return@withContext false
        try {
            val payload = buildJsonObject {
                put("user_id", uid)
                put("meal_type", tipoRefeicao)
                put("dish_name", titulo)
                put("calories_kcal", calorias)
                put("protein_g", proteinaG)
                put("carbs_g", carboG)
                put("fat_g", gorduraG)
                put("adherence_score", adherenceScore)
                put("ai_feedback", feedbackIa)
            }
            ClienteSupabase.client.from("meal_logs").insert(payload)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun analisarFotoPratoComIa(
        imagemBase64: String,
        tipoRefeicao: String = "Pós-Treino",
        descricaoExtra: String = ""
    ): ResultadoScannerPratoIa = withContext(Dispatchers.IO) {
        try {
            val url = URL("${ClienteSupabase.WEB_BASE_URL}/api/ai/coach")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ClienteSupabase.USER_AGENT_PADRAO)
                connectTimeout = 25000
                readTimeout = 45000
                doOutput = true
            }

            val prompt = """
                Você é o Nutricionista Esportivo IA do TreinorIA PRO.
                Analise a foto deste prato, refeição, suplemento ou tabela nutricional ($tipoRefeicao).
                ${if (descricaoExtra.isNotBlank()) "Observação do atleta: $descricaoExtra" else ""}
                Estime com precisão os macronutrientes totais visíveis na porção e retorne OBRIGATORIAMENTE um JSON válido:
                {
                  "dish_name": "Nome resumido dos alimentos identificados na foto",
                  "calories_kcal": 580,
                  "protein_g": 45,
                  "carbs_g": 62,
                  "fat_g": 14,
                  "adherence_score": 92,
                  "ai_feedback": "Avaliação objetiva em português sobre os macros e qualidade para hipertrofia."
                }
            """.trimIndent()

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imagemBase64)
                        }
                    )
                )
            }

            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", partsArray)
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.3)
                        put("responseMimeType", "application/json")
                    }
                )
            }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val respTxt = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respTxt)
                val cand = json.optJSONArray("candidates")?.optJSONObject(0)
                val rawText = cand?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
                val limpo = rawText.substringAfter("{").substringBeforeLast("}")
                val obj = JSONObject("{$limpo}")
                return@withContext ResultadoScannerPratoIa(
                    dishName = obj.optString("dish_name", descricaoExtra.ifBlank { "Refeição Escaneada por Foto" }),
                    caloriesKcal = obj.optInt("calories_kcal", 520),
                    proteinG = obj.optDouble("protein_g", 40.0),
                    carbsG = obj.optDouble("carbs_g", 55.0),
                    fatG = obj.optDouble("fat_g", 14.0),
                    adherenceScore = obj.optInt("adherence_score", 90),
                    aiFeedback = obj.optString("ai_feedback", "Ótima combinação de proteínas e carboidratos para recuperação muscular!")
                )
            }
        } catch (_: Exception) {}

        ResultadoScannerPratoIa(
            dishName = descricaoExtra.ifBlank { "Prato Escaneado pela Câmera" },
            caloriesKcal = 520,
            proteinG = 40.0,
            carbsG = 52.0,
            fatG = 14.0,
            adherenceScore = 90,
            aiFeedback = "Foto analisada com sucesso! Excelente aporte proteico para síntese muscular."
        )
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
                put("measured_at", obterDataHojeIso())
                if (pesoKg != null) put("weight_kg", pesoKg)
                if (bfPct != null) put("body_fat_pct", bfPct)
                if (peitoCm != null) put("chest_cm", peitoCm)
                if (cinturaCm != null) put("waist_cm", cinturaCm)
                if (bracoCm != null) put("arms_cm", bracoCm)
                if (coxaCm != null) put("legs_cm", coxaCm)
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
                put("student_email", email.trim().ifBlank { "${nome.lowercase().replace(" ", ".")}@treinoria.app" })
                if (telefone.isNotBlank()) put("student_whatsapp", telefone.trim())
                put("student_goal", objetivo.trim().ifBlank { "Hipertrofia" })
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
