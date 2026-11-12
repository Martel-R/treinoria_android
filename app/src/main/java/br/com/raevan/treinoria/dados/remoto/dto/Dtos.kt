package br.com.raevan.treinoria.dados.remoto.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PerfilDto(
    val id: String = "",
    val name: String = "Atleta",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
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
    @SerialName("assigned_gym_id") val assignedGymId: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    val goal: String = "Hipertrofia",
    @SerialName("weekly_goal_days") val weeklyGoal: Int = 5,
    @SerialName("default_rest_seconds") val defaultRestSeconds: Int = 90
)

@Serializable
data class ExercicioDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    val name: String = "",
    @SerialName("muscle_group") val muscleGroup: String = "",
    val equipment: String = "Barra",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val instructions: String? = null,
    @SerialName("is_custom") val isCustom: Boolean = false
)

@Serializable
data class ItemFichaTreinoDto(
    val id: String = "",
    @SerialName("plan_id") val planId: String = "",
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("target_sets") val targetSets: Int = 4,
    @SerialName("target_reps") val targetReps: String = "8-12",
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int = 90,
    val notes: String? = null,
    val exercise: ExercicioDto? = null
)

@Serializable
data class FichaTreinoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val description: String? = null,
    val color: String = "#10b981",
    @SerialName("day_of_week") val dayOfWeek: String? = null,
    @SerialName("pre_workout_meal") val preWorkoutMeal: String? = null,
    @SerialName("post_workout_meal") val postWorkoutMeal: String? = null,
    @SerialName("prescribed_by_name") val prescribedByName: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val items: List<ItemFichaTreinoDto> = emptyList()
)

@Serializable
data class SerieSessaoDto(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("set_number") val setNumber: Int = 1,
    @SerialName("weight_kg") val weightKg: Double = 0.0,
    val reps: Int = 10,
    val rpe: Double? = null,
    @SerialName("is_warmup") val isWarmup: Boolean = false,
    @SerialName("is_completed") val isCompleted: Boolean = true,
    @SerialName("performed_at") val performedAt: String? = null
)

@Serializable
data class SessaoTreinoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("plan_id") val planId: String? = null,
    val title: String = "",
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("total_volume_kg") val totalVolumeKg: Double = 0.0,
    val feeling: String? = "Ótimo",
    val notes: String? = null
)

@Serializable
data class HabitoDiarioDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("water_ml") val waterMl: Int = 0,
    @SerialName("water_goal_ml") val waterGoalMl: Int = 3000,
    @SerialName("creatine_taken") val creatineTaken: Boolean = false,
    @SerialName("pre_workout_taken") val preWorkoutTaken: Boolean = false,
    @SerialName("post_workout_taken") val postWorkoutTaken: Boolean = false,
    @SerialName("sleep_hours") val sleepHours: Double? = null
)

@Serializable
data class RefeicaoLogDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("meal_type") val mealType: String = "Almoço / Pós-Treino",
    @SerialName("dish_name") val dishName: String = "",
    @SerialName("calories_kcal") val caloriesKcal: Int = 0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("adherence_score") val adherenceScore: Int? = null,
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
    @SerialName("arms_cm") val armsCm: Double? = null,
    @SerialName("legs_cm") val legsCm: Double? = null,
    val notes: String? = null
)

@Serializable
data class FotoProgressoDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("photo_url") val photoUrl: String = "",
    val category: String = "Evolução",
    @SerialName("weight_kg") val weightKg: Double? = null,
    val caption: String? = null,
    @SerialName("taken_at") val takenAt: String = ""
)

@Serializable
data class VinculoAlunoPersonalDto(
    val id: String = "",
    @SerialName("trainer_id") val trainerId: String = "",
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("student_name") val studentName: String = "",
    @SerialName("student_email") val studentEmail: String? = null,
    @SerialName("student_whatsapp") val studentWhatsapp: String? = null,
    @SerialName("student_goal") val studentGoal: String = "Hipertrofia",
    val status: String = "active",
    val notes: String? = null
)

@Serializable
data class ServicoAcademiaDto(
    val id: String = "",
    @SerialName("gym_id") val gymId: String = "",
    val name: String = "",
    val category: String = "Modalidades & Aulas",
    val description: String? = null,
    @SerialName("price_brl") val priceBrl: Double = 0.0,
    @SerialName("duration_minutes") val durationMinutes: Int = 45,
    @SerialName("max_students_per_class") val maxStudentsPerClass: Int = 15,
    val weekdays: List<String> = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta"),
    @SerialName("slots_per_day") val slotsPerDay: Int = 4,
    @SerialName("time_slots") val timeSlots: List<String> = listOf("07:00", "09:00", "18:00", "19:00"),
    @SerialName("instructor_name") val instructorName: String? = null,
    @SerialName("room_or_space") val roomOrSpace: String? = "Sala Principal",
    @SerialName("schedule_info") val scheduleInfo: String? = "Seg a Sex • 4 horários/dia",
    @SerialName("included_in_plans") val includedInPlans: List<String> = emptyList(),
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class PlanoMatriculaAcademiaDto(
    val id: String = "",
    @SerialName("gym_id") val gymId: String = "",
    val name: String = "",
    @SerialName("billing_cycle") val billingCycle: String = "Mensal",
    @SerialName("price_brl") val priceBrl: Double = 119.90,
    @SerialName("enrollment_fee_brl") val enrollmentFeeBrl: Double = 0.0,
    @SerialName("fidelity_months") val fidelityMonths: Int = 0,
    @SerialName("allowed_hours") val allowedHours: String = "Horário Livre (05h às 23h)",
    @SerialName("max_checkins_per_week") val maxCheckinsPerWeek: Int = 7,
    @SerialName("guest_passes_per_month") val guestPassesPerMonth: Int = 0,
    val description: String? = null,
    val perks: List<String> = emptyList(),
    @SerialName("included_service_names") val includedServiceNames: List<String> = emptyList(),
    @SerialName("active_students_count") val activeStudentsCount: Int = 0,
    @SerialName("is_highlighted") val isHighlighted: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class AgendamentoServicoAcademiaDto(
    val id: String = "",
    @SerialName("gym_id") val gymId: String = "",
    @SerialName("service_id") val serviceId: String = "",
    @SerialName("service_name") val serviceName: String = "",
    @SerialName("student_user_id") val studentUserId: String? = null,
    @SerialName("student_name") val studentName: String = "",
    @SerialName("student_whatsapp") val studentWhatsapp: String? = null,
    @SerialName("student_plan_name") val studentPlanName: String? = "Plano Gold Trimestral",
    @SerialName("booking_type") val bookingType: String = "included_in_plan",
    @SerialName("price_charged_brl") val priceChargedBrl: Double = 0.0,
    @SerialName("preferred_day_of_week") val preferredDayOfWeek: String? = null,
    @SerialName("preferred_date") val preferredDate: String? = null,
    @SerialName("preferred_time") val preferredTime: String? = null,
    val status: String = "confirmed",
    val notes: String? = null
)
