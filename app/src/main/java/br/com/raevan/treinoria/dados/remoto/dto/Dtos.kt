package br.com.raevan.treinoria.dados.remoto.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PerfilDto(
    val id: String = "",
    val name: String = "Atleta",
    val email: String? = null,
    val cpf: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("account_role") val accountRole: String = "athlete",
    @SerialName("cref_register") val crefRegister: String? = null,
    @SerialName("gym_brand_name") val gymBrandName: String? = null,
    @SerialName("whatsapp_phone") val whatsappPhone: String? = null,
    @SerialName("subscription_status") val subscriptionStatus: String = "trial",
    @SerialName("subscription_plan") val subscriptionPlan: String = "trial_7d",
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    @SerialName("assigned_trainer_id") val assignedTrainerId: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    val goal: String = "Hipertrofia & Força",
    @SerialName("weekly_goal") val weeklyGoal: Int = 4,
    @SerialName("water_goal_ml") val waterGoalMl: Int = 3000
)

@Serializable
data class ExercicioDto(
    val id: String = "",
    val name: String = "",
    @SerialName("muscle_group") val muscleGroup: String = "",
    val equipment: String = "Barra",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val instructions: String? = null,
    @SerialName("is_custom") val isCustom: Boolean = false
)

@Serializable
data class TemplateExercicioDto(
    val id: String = "",
    @SerialName("template_id") val templateId: String = "",
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("target_sets") val targetSets: Int = 3,
    @SerialName("target_reps") val targetReps: String = "8-12",
    @SerialName("target_weight_kg") val targetWeightKg: Double = 0.0,
    @SerialName("rest_seconds") val restSeconds: Int = 90,
    val notes: String? = null,
    val exercise: ExercicioDto? = null
)

@Serializable
data class FichaTreinoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "",
    val subtitle: String? = null,
    @SerialName("accent_color") val accentColor: String = "#10b981",
    @SerialName("day_of_week") val dayOfWeek: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("template_exercises") val templateExercises: List<TemplateExercicioDto> = emptyList()
)

@Serializable
data class SerieSessaoDto(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("set_number") val setNumber: Int = 1,
    @SerialName("weight_kg") val weightKg: Double = 0.0,
    val reps: Int = 10,
    val rpe: Double? = null,
    @SerialName("is_warmup") val isWarmup: Boolean = false,
    val completed: Boolean = true
)

@Serializable
data class SessaoTreinoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("template_id") val templateId: String? = null,
    val title: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("total_volume_kg") val totalVolumeKg: Double = 0.0,
    @SerialName("total_sets") val totalSets: Int = 0,
    val feeling: String? = "Forte 🔥",
    val notes: String? = null
)

@Serializable
data class HabitoDiarioDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("water_ml") val waterMl: Int = 0,
    @SerialName("creatine_taken") val creatineTaken: Boolean = false,
    @SerialName("sleep_hours") val sleepHours: Double? = null
)

@Serializable
data class RefeicaoLogDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("meal_title") val mealTitle: String = "",
    val calories: Int = 0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("ai_feedback") val aiFeedback: String? = null,
    @SerialName("logged_at") val loggedAt: String = ""
)

@Serializable
data class MedidaCorporalDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("measured_at") val measuredAt: String = "",
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("body_fat_pct") val bodyFatPct: Double? = null,
    @SerialName("chest_cm") val chestCm: Double? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("arm_cm") val armCm: Double? = null,
    @SerialName("thigh_cm") val thighCm: Double? = null,
    val notes: String? = null
)

@Serializable
data class VinculoAlunoPersonalDto(
    val id: String = "",
    @SerialName("trainer_id") val trainerId: String = "",
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("student_name") val studentName: String = "",
    @SerialName("student_email") val studentEmail: String? = null,
    @SerialName("student_phone") val studentPhone: String? = null,
    val goal: String = "Hipertrofia",
    val status: String = "active",
    val notes: String? = null
)
