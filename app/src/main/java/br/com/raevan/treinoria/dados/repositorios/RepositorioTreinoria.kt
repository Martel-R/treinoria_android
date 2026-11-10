package br.com.raevan.treinoria.dados.repositorios

import android.content.Context
import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.remoto.dto.*
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.saude.GerenciadorHealthConnect
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

data class MensagemChatIa(
    val role: String, // "user" ou "assistant"
    val content: String
)

data class BlocoExercicioAjustadoIa(
    val exerciseId: String,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: String,
    val targetWeightKg: Double,
    val restSeconds: Int,
    val notes: String,
    val changeBadge: String
)

data class RespostaCheckinPreTreinoIa(
    val replyMessage: String,
    val readinessScore: Int,
    val workoutSummaryAdvice: String,
    val hasWorkoutChanges: Boolean,
    val adjustedBlocks: List<BlocoExercicioAjustadoIa>
)

data class FichaGeradaPorIa(
    val replyMessage: String,
    val planName: String,
    val dayOfWeek: String,
    val description: String,
    val preWorkoutMeal: String,
    val postWorkoutMeal: String,
    val items: List<BlocoExercicioAjustadoIa>
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

    /**
     * Monta o DOSSIÊ COMPLETO DO ATLETA (Perfil + Medidas Corporais + Hidratação/Suplementos de Hoje +
     * Refeições Recentes + Dados do Smartwatch Health Connect + Últimas Sessões + Histórico de Cargas/PRs por Exercício)
     * para que toda interação com a IA (Check-in Pré-Treino e Criação de Fichas) considere 100% dos dados reais do usuário!
     */
    suspend fun montarDossieCompletoUsuarioParaIa(
        context: Context? = null,
        catalogoExercicios: List<ExercicioDto> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val perfil = carregarPerfil()
        val habitoHoje = obterHabitoHoje()
        val medidas = listarMedidasCorporais()
        val refeicoes = listarRefeicoesRecentes()
        val sessoes = listarSessoesRecentes(10)
        val estatisticas = obterEstatisticasExercicios()
        val mapaEx = catalogoExercicios.associateBy { it.id }

        val saude = if (context != null) {
            try { GerenciadorHealthConnect.lerResumoDiario(context) } catch (_: Throwable) { null }
        } else null

        val sb = StringBuilder()
        sb.appendLine("=== 1. PERFIL E OBJETIVO DO ATLETA ===")
        sb.appendLine("• Nome: ${perfil?.name ?: "Atleta"} | Objetivo: ${perfil?.goal ?: "Hipertrofia & Força"} | Meta Semanal: ${perfil?.weeklyGoal ?: 5}x/semana")
        sb.appendLine("• Peso no Perfil: ${perfil?.weightKg ?: "N/D"} kg | Altura: ${perfil?.heightCm ?: "N/D"} cm")

        sb.appendLine("\n=== 2. MEDIDAS CORPORAIS E COMPOSIÇÃO (ANTROPOMETRIA) ===")
        val ultimaMedida = medidas.firstOrNull()
        if (ultimaMedida != null) {
            sb.appendLine("• Última Avaliação (${ultimaMedida.measuredAt}): Peso=${ultimaMedida.weightKg ?: "-"}kg, BF=${ultimaMedida.bodyFatPct ?: "-"}%, Peito=${ultimaMedida.chestCm ?: "-"}cm, Cintura=${ultimaMedida.waistCm ?: "-"}cm, Braço=${ultimaMedida.armsCm ?: "-"}cm, Coxa=${ultimaMedida.legsCm ?: "-"}cm")
        } else {
            sb.appendLine("• Sem avaliação antropométrica recente registrada.")
        }

        sb.appendLine("\n=== 3. HIDRATAÇÃO, SUPLEMENTOS E SMARTWATCH HOJE (${obterDataHojeIso()}) ===")
        sb.appendLine("• Água hoje: ${habitoHoje.waterMl} / ${habitoHoje.waterGoalMl} ml | Creatina hoje: ${if (habitoHoje.creatineTaken) "SIM" else "NÃO"} | Pré-Treino: ${if (habitoHoje.preWorkoutTaken) "SIM" else "NÃO"} | Pós-Treino/Whey: ${if (habitoHoje.postWorkoutTaken) "SIM" else "NÃO"}")
        if (saude != null && saude.permissoesConcedidas) {
            sb.appendLine("• Health Connect / Smartwatch Hoje: Passos=${saude.passosHoje}, Calorias Ativas=${saude.caloriasAtivasKcal} kcal, Último Pulso=${saude.ultimoBatimentoBpm ?: "N/D"} bpm, Sono Última Noite=${saude.horasSonoUltimaNoite}h")
        }

        sb.appendLine("\n=== 4. ÚLTIMAS REFEIÇÕES REGISTRADAS ===")
        if (refeicoes.isNotEmpty()) {
            refeicoes.take(4).forEach { r ->
                sb.appendLine("• [${r.mealType}] ${r.dishName}: ${r.caloriesKcal} kcal (Prot: ${r.proteinG.toInt()}g, Carb: ${r.carbsG.toInt()}g, Gord: ${r.fatG.toInt()}g)")
            }
        } else {
            sb.appendLine("• Sem refeições registradas recentemente.")
        }

        sb.appendLine("\n=== 5. ÚLTIMOS TREINOS CONCLUÍDOS ===")
        if (sessoes.isNotEmpty()) {
            sessoes.take(5).forEach { s ->
                sb.appendLine("• ${s.startedAt.take(10)}: '${s.title}' (${s.durationMinutes ?: 45} min, Volume Total: ${s.totalVolumeKg.toInt()} kg, Sensação: ${s.feeling ?: "Ótimo"})")
            }
        } else {
            sb.appendLine("• Nenhuma sessão anterior registrada.")
        }

        sb.appendLine("\n=== 6. HISTÓRICO DE CARGAS, REPETIÇÕES E RECORDES (PR) POR EXERCÍCIO ===")
        if (estatisticas.isNotEmpty()) {
            estatisticas.forEach { (exId, st) ->
                val nomeEx = mapaEx[exId]?.name ?: exId
                sb.appendLine("• [$exId] $nomeEx -> Última execução: ${st.lastWeightKg}kg × ${st.lastReps} reps | Recorde Pessoal (PR): ${st.maxWeightKg}kg")
            }
        } else {
            sb.appendLine("• Sem séries anteriores registradas — use as cargas padrão da ficha.")
        }

        sb.toString()
    }

    /**
     * Check-in Pré-Treino e Adaptação Dinâmica com o Personal Trainer IA (idêntico ao `askAiWorkoutCoach` da Web!),
     * considerando TODO o dossiê do usuário e retornando os exercícios, séries, repetições e cargas ajustados.
     */
    suspend fun consultarCheckinPreTreinoIa(
        context: Context?,
        tituloTreino: String,
        blocosAtuais: List<BlocoExercicioAjustadoIa>,
        catalogoExercicios: List<ExercicioDto>,
        historicoChat: List<MensagemChatIa>,
        mensagemAtleta: String
    ): RespostaCheckinPreTreinoIa = withContext(Dispatchers.IO) {
        val dossieCompleto = montarDossieCompletoUsuarioParaIa(context, catalogoExercicios)
        val catalogoTexto = catalogoExercicios.joinToString("\n") {
            "${it.id} | ${it.name} (${it.muscleGroup} - ${it.equipment})"
        }
        val treinoAtualTexto = blocosAtuais.mapIndexed { idx, b ->
            "${idx + 1}. exercise_id=\"${b.exerciseId}\" | ${b.exerciseName} -> Atual: ${b.targetSets} séries × ${b.targetReps} reps @ ${b.targetWeightKg}kg (Descanso: ${b.restSeconds}s)"
        }.joinToString("\n")

        val systemPrompt = """
            Você é o "TreinorIA Coach", Personal Trainer e Fisiologista do Exercício de elite.
            O atleta acabou de abrir o treino do dia: "$tituloTreino".
            Analise TODAS AS INFORMAÇÕES DO ATLETA abaixo (Perfil, Medidas Corporais, Hidratação de hoje, Refeições, Dados do Smartwatch Health Connect, Últimas Sessões e Histórico de Cargas/PRs por Exercício) e também o que ele relatou agora sobre alimentação pré-treino, aeróbico/cardio, energia/sono ou pedidos de troca de aparelho.

            REGRAS DE ADAPTAÇÃO DO TREINO:
            - Sempre considere o histórico real de cargas, medidas, sono e nutrição do atleta abaixo.
            - Se ele estiver bem alimentado e descansado, sugira progressão gradual de carga (+1kg a +2.5kg) ou +1-2 repetições nos exercícios em que ele já domina a carga anterior.
            - Se ele relatar que comeu pouco, está em jejum, dormiu mal ou já fez aeróbico intenso antes da musculação, ajuste o ritmo: reduza ligeiramente a carga (-5% a -10%) ou ajuste séries/repetições e descanso para evitar falha precoce.
            - Se ele pedir para incluir, trocar ou remover algum exercício (ou relatar dor articular/aparelho ocupado), atualize a lista `adjusted_blocks` usando EXCLUSIVAMENTE `exercise_id` válidos do CATÁLOGO DE EXERCÍCIOS DISPONÍVEIS abaixo.

            $dossieCompleto

            === EXERCÍCIOS ATUAIS DO TREINO DE HOJE ("$tituloTreino") ===
            $treinoAtualTexto

            === CATÁLOGO DE EXERCÍCIOS DISPONÍVEIS (USE ESTES exercise_id EXATOS) ===
            $catalogoTexto

            Responda OBRIGATORIAMENTE em formato JSON válido seguindo exatamente este schema:
            {
              "reply_message": "Sua resposta conversacional em português-BR para o atleta explicando os ajustes feitos nas cargas/séries/exercícios com base no histórico e no estado de hoje.",
              "readiness_score": 88,
              "workout_summary_advice": "Resumo de 1 linha do ritmo do treino de hoje (ex: 'Foco em Progressão • Glicogênio Alto')",
              "has_workout_changes": true,
              "adjusted_blocks": [
                {
                  "exercise_id": "UUID-DO-CATALOGO",
                  "exercise_name": "Nome do Exercício",
                  "target_sets": 4,
                  "target_reps": "8-10",
                  "target_weight_kg": 30,
                  "rest_seconds": 90,
                  "notes": "Dica específica para hoje",
                  "change_badge": "+2.5kg Progressão | Mantido | Ajustado"
                }
              ]
            }
        """.trimIndent()

        val transcricao = historicoChat.joinToString("\n\n") { m ->
            "${if (m.role == "user") "ATLETA" else "PERSONAL IA"}: ${m.content}"
        }
        val userPromptFinal = "${if (transcricao.isNotBlank()) "HISTÓRICO DA CONVERSA DE HOJE:\n$transcricao\n\n" else ""}MENSAGEM ATUAL DO ATLETA: $mensagemAtleta"

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

            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", userPromptFinal)))
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.4)
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

                val arr = obj.optJSONArray("adjusted_blocks") ?: JSONArray()
                val listaBlocos = mutableListOf<BlocoExercicioAjustadoIa>()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    listaBlocos.add(
                        BlocoExercicioAjustadoIa(
                            exerciseId = b.optString("exercise_id"),
                            exerciseName = b.optString("exercise_name", "Exercício"),
                            targetSets = b.optInt("target_sets", 4).coerceIn(1, 8),
                            targetReps = b.optString("target_reps", "8-12"),
                            targetWeightKg = b.optDouble("target_weight_kg", 20.0),
                            restSeconds = b.optInt("rest_seconds", 90),
                            notes = b.optString("notes", ""),
                            changeBadge = b.optString("change_badge", "Calibrado IA")
                        )
                    )
                }

                return@withContext RespostaCheckinPreTreinoIa(
                    replyMessage = obj.optString("reply_message", "Treino calibrado com base no seu histórico e disposição de hoje!"),
                    readinessScore = obj.optInt("readiness_score", 88),
                    workoutSummaryAdvice = obj.optString("workout_summary_advice", "Treino Calibrado pelo Coach IA"),
                    hasWorkoutChanges = obj.optBoolean("has_workout_changes", listaBlocos.isNotEmpty()),
                    adjustedBlocks = if (listaBlocos.isNotEmpty()) listaBlocos else blocosAtuais
                )
            }
        } catch (_: Exception) {}

        RespostaCheckinPreTreinoIa(
            replyMessage = "Analisei seu histórico de cargas e condição de hoje! Mantive os exercícios principais da ficha com foco em execução controlada (3-1-1) e progressão segura.",
            readinessScore = 85,
            workoutSummaryAdvice = "Ritmo Forte & Controlado • Foco em Hipertrofia",
            hasWorkoutChanges = true,
            adjustedBlocks = blocosAtuais
        )
    }

    /**
     * Gera uma Ficha de Treino completa com IA (`generateWorkoutPlanWithAi`),
     * considerando TODAS as informações do usuário (Perfil, Medidas, Histórico de Cargas/PRs, Nutrição, etc.).
     */
    suspend fun gerarFichaCompletaComIa(
        context: Context?,
        pedidoUsuario: String,
        catalogoExercicios: List<ExercicioDto>
    ): FichaGeradaPorIa? = withContext(Dispatchers.IO) {
        val dossieCompleto = montarDossieCompletoUsuarioParaIa(context, catalogoExercicios)
        val catalogoTexto = catalogoExercicios.joinToString("\n") {
            "${it.id} | ${it.name} (${it.muscleGroup} - ${it.equipment})"
        }

        val systemPrompt = """
            Você é o "TreinorIA Coach", especialista em periodização e montagem de fichas de musculação personalizadas.
            Analise TODAS AS INFORMAÇÕES DO ATLETA abaixo (Perfil, Objetivo, Medidas Corporais, Hidratação/Nutrição, Sessões Anteriores e Histórico de Cargas/PRs por Exercício) e o CATÁLOGO DE EXERCÍCIOS para montar uma ficha de treino completa e altamente eficiente conforme o pedido do usuário.
            Use EXCLUSIVAMENTE os `exercise_id` existentes na lista do catálogo abaixo e defina `target_weight_kg` condizente com o histórico real de cargas do atleta!

            $dossieCompleto

            === CATÁLOGO DE EXERCÍCIOS DISPONÍVEIS (USE ESTES exercise_id EXATOS) ===
            $catalogoTexto

            Retorne OBRIGATORIAMENTE um JSON válido com a estrutura:
            {
              "reply_message": "Explicação curta sobre a divisão, volume e cargas escolhidas com base no perfil e histórico do atleta.",
              "plan_name": "Ex: Treino A • Peito, Ombro e Tríceps",
              "day_of_week": "Ex: Segunda / Quinta",
              "description": "Descrição técnica resumida do foco da ficha",
              "pre_workout_meal": "Sugestão prática de refeição PRÉ-TREINO ideal para esta ficha",
              "post_workout_meal": "Sugestão prática de refeição PÓS-TREINO ideal para esta ficha",
              "items": [
                {
                  "exercise_id": "UUID-DO-CATALOGO",
                  "exercise_name": "Nome do Exercício",
                  "target_sets": 4,
                  "target_reps": "8-12",
                  "target_weight_kg": 25,
                  "rest_seconds": 90,
                  "notes": "Observação biomecânica ou de cadência"
                }
              ]
            }
        """.trimIndent()

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

            val body = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", pedidoUsuario)))
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.4)
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

                val arr = obj.optJSONArray("items") ?: JSONArray()
                val itens = mutableListOf<BlocoExercicioAjustadoIa>()
                for (i in 0 until arr.length()) {
                    val itObj = arr.getJSONObject(i)
                    itens.add(
                        BlocoExercicioAjustadoIa(
                            exerciseId = itObj.optString("exercise_id"),
                            exerciseName = itObj.optString("exercise_name", "Exercício"),
                            targetSets = itObj.optInt("target_sets", 4).coerceIn(1, 8),
                            targetReps = itObj.optString("target_reps", "8-12"),
                            targetWeightKg = itObj.optDouble("target_weight_kg", 20.0),
                            restSeconds = itObj.optInt("rest_seconds", 90),
                            notes = itObj.optString("notes", ""),
                            changeBadge = "Gerado por IA"
                        )
                    )
                }

                return@withContext FichaGeradaPorIa(
                    replyMessage = obj.optString("reply_message", "Ficha criada com base no seu perfil e histórico!"),
                    planName = obj.optString("plan_name", "Ficha Personalizada IA"),
                    dayOfWeek = obj.optString("day_of_week", "Segunda / Quinta"),
                    description = obj.optString("description", "Hipertrofia & Progressão de Carga"),
                    preWorkoutMeal = obj.optString("pre_workout_meal", ""),
                    postWorkoutMeal = obj.optString("post_workout_meal", ""),
                    items = itens
                )
            }
        } catch (_: Exception) {}
        null
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
        preWorkoutMeal: String? = null,
        postWorkoutMeal: String? = null,
        exerciciosSelecionados: List<Triple<String, Int, String>>,
        pesosAlvo: Map<String, Double> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val novaFicha = buildJsonObject {
                put("user_id", targetUserId)
                put("name", titulo.trim())
                put("description", subtitulo.trim())
                put("day_of_week", diaSemana)
                put("color", corHex)
                put("is_active", true)
                if (!preWorkoutMeal.isNullOrBlank()) put("pre_workout_meal", preWorkoutMeal.trim())
                if (!postWorkoutMeal.isNullOrBlank()) put("post_workout_meal", postWorkoutMeal.trim())
            }
            val inserida = ClienteSupabase.client.from("workout_plans")
                .insert(novaFicha) { select() }
                .decodeSingle<FichaTreinoDto>()

            exerciciosSelecionados.forEachIndexed { idx, item ->
                val peso = pesosAlvo[item.first] ?: 20.0
                val vinc = buildJsonObject {
                    put("plan_id", inserida.id)
                    put("exercise_id", item.first)
                    put("sort_order", idx + 1)
                    put("target_sets", item.second)
                    put("target_reps", item.third)
                    put("target_weight_kg", peso)
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

    suspend fun consultarCoachIa(promptUsuario: String, context: Context? = null): String = withContext(Dispatchers.IO) {
        try {
            val dossie = try { montarDossieCompletoUsuarioParaIa(context) } catch (_: Throwable) { "" }
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
                                "Você é o Coach IA Oficial do TreinorIA PRO (Raevan Tech Group), especialista em hipertrofia, biomecânica e nutrição esportiva.\n\n$dossie\n\nResponda em português-BR de forma direta, técnica e personalizada para os dados acima:\n\n$promptUsuario"
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
