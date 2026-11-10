package br.com.raevan.treinoria.nucleo.saude

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class ResumoSaudeDiaria(
    val disponivelNoAparelho: Boolean = false,
    val permissoesConcedidas: Boolean = false,
    val precisaInstalarAppHealthConnect: Boolean = false,
    val passosHoje: Long = 0L,
    val caloriasAtivasKcal: Int = 0,
    val ultimoBatimentoBpm: Long? = null,
    val horasSonoUltimaNoite: Double = 0.0,
    val ultimoPesoKg: Double? = null
)

object GerenciadorHealthConnect {

    const val PACOTE_HEALTH_CONNECT = "com.google.android.apps.healthdata"

    val PERMISSOES_SAUDE = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )

    fun verificarStatusSdk(context: Context): Int {
        return try {
            HealthConnectClient.getSdkStatus(context, PACOTE_HEALTH_CONNECT)
        } catch (_: Throwable) {
            HealthConnectClient.SDK_UNAVAILABLE
        }
    }

    fun obterCliente(context: Context): HealthConnectClient? {
        return try {
            if (verificarStatusSdk(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun abrirConfiguracoesOuPlayStoreHealthConnect(context: Context) {
        try {
            val intentConfig = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            intentConfig.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intentConfig)
        } catch (_: Throwable) {
            try {
                val uri = Uri.parse("market://details?id=$PACOTE_HEALTH_CONNECT")
                val intentLoja = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intentLoja)
            } catch (_: Throwable) {}
        }
    }

    suspend fun temPermissoesConcedidas(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cliente = obterCliente(context) ?: return@withContext false
        try {
            val concedidas = cliente.permissionController.getGrantedPermissions()
            // Considera conectado se tiver pelo menos permissão de Passos ou Exercício
            concedidas.containsAll(PERMISSOES_SAUDE) ||
                concedidas.contains(HealthPermission.getReadPermission(StepsRecord::class)) ||
                concedidas.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Lê Passos de Hoje, Calorias Ativas, Último Batimento Cardíaco (BPM),
     * Horas de Sono nas últimas 24h e Último Peso Corporal sincronizados pelo
     * Google Fit / Samsung Health / Galaxy Watch / Mi Fitness / Zepp / Garmin.
     */
    suspend fun lerResumoDiario(context: Context): ResumoSaudeDiaria = withContext(Dispatchers.IO) {
        val status = verificarStatusSdk(context)
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            return@withContext ResumoSaudeDiaria(
                disponivelNoAparelho = false,
                permissoesConcedidas = false,
                precisaInstalarAppHealthConnect = true
            )
        }
        val cliente = obterCliente(context) ?: return@withContext ResumoSaudeDiaria(
            disponivelNoAparelho = false,
            permissoesConcedidas = false
        )

        val concedidas = try {
            cliente.permissionController.getGrantedPermissions()
        } catch (_: Throwable) {
            emptySet()
        }

        val temAlgumaPermissao = concedidas.isNotEmpty()
        if (!temAlgumaPermissao) {
            return@withContext ResumoSaudeDiaria(
                disponivelNoAparelho = true,
                permissoesConcedidas = false
            )
        }

        val fuso = ZoneId.systemDefault()
        val inicioHoje = LocalDate.now(fuso).atStartOfDay(fuso).toInstant()
        val agora = Instant.now()
        val ontem24h = agora.minus(Duration.ofHours(24))
        val ultimos30Dias = agora.minus(Duration.ofDays(30))

        // 1. Passos e Calorias Ativas Hoje
        var passos = 0L
        var kcalAtivas = 0
        try {
            val agregados = cliente.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(inicioHoje, agora)
                )
            )
            passos = agregados[StepsRecord.COUNT_TOTAL] ?: 0L
            kcalAtivas = agregados[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0
        } catch (_: Throwable) {}

        // 2. Último Batimento Cardíaco (BPM) nas últimas 24h
        var ultimoBpm: Long? = null
        try {
            val respHr = cliente.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(ontem24h, agora)
                )
            )
            val ultimaAmostra = respHr.records
                .flatMap { it.samples }
                .maxByOrNull { it.time }
            ultimoBpm = ultimaAmostra?.beatsPerMinute
        } catch (_: Throwable) {}

        // 3. Horas de Sono nas últimas 24h
        var horasSono = 0.0
        try {
            val respSono = cliente.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(ontem24h, agora)
                )
            )
            val minutosTotais = respSono.records.sumOf { sessao ->
                Duration.between(sessao.startTime, sessao.endTime).toMinutes().coerceAtLeast(0)
            }
            horasSono = Math.round((minutosTotais / 60.0) * 10.0) / 10.0
        } catch (_: Throwable) {}

        // 4. Último Peso Corporal registrado (Balança Smart / Google Fit / Samsung Health)
        var ultimoPeso: Double? = null
        try {
            val respPeso = cliente.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(ultimos30Dias, agora)
                )
            )
            val regMaisRecente = respPeso.records.maxByOrNull { it.time }
            if (regMaisRecente != null) {
                ultimoPeso = Math.round(regMaisRecente.weight.inKilograms * 10.0) / 10.0
            }
        } catch (_: Throwable) {}

        ResumoSaudeDiaria(
            disponivelNoAparelho = true,
            permissoesConcedidas = true,
            passosHoje = passos,
            caloriasAtivasKcal = kcalAtivas,
            ultimoBatimentoBpm = ultimoBpm,
            horasSonoUltimaNoite = horasSono,
            ultimoPesoKg = ultimoPeso
        )
    }

    /**
     * Grava automaticamente a sessão de Musculação concluída + Calorias Queimadas
     * no Health Connect (aparece no Google Fit, Samsung Health, etc.).
     */
    suspend fun gravarTreinoConcluido(
        context: Context,
        tituloTreino: String,
        duracaoSegundos: Int,
        volumeTotalKg: Double,
        notas: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cliente = obterCliente(context) ?: return@withContext false
        try {
            val fim = Instant.now()
            val inicio = fim.minusSeconds(duracaoSegundos.toLong().coerceAtLeast(60L))
            val offset = ZonedDateTime.now().offset

            // Estima gasto calórico da sessão de musculação (~6 kcal/min + bônus por tonelagem)
            val minutos = (duracaoSegundos / 60.0).coerceAtLeast(5.0)
            val kcalEstimadas = (minutos * 6.2 + (volumeTotalKg * 0.015)).coerceIn(80.0, 950.0)

            val sessaoRecord = ExerciseSessionRecord(
                startTime = inicio,
                startZoneOffset = offset,
                endTime = fim,
                endZoneOffset = offset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                title = "TreinorIA • $tituloTreino",
                notes = "Volume total: ${volumeTotalKg.toInt()} kg. $notas".trim()
            )

            val caloriasRecord = ActiveCaloriesBurnedRecord(
                startTime = inicio,
                startZoneOffset = offset,
                endTime = fim,
                endZoneOffset = offset,
                energy = Energy.kilocalories(kcalEstimadas)
            )

            cliente.insertRecords(listOf(sessaoRecord, caloriasRecord))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Grava ingestão de água (ml) no Health Connect.
     */
    suspend fun gravarAguaIngerida(context: Context, quantidadeMl: Int): Boolean = withContext(Dispatchers.IO) {
        val cliente = obterCliente(context) ?: return@withContext false
        try {
            val fim = Instant.now()
            val inicio = fim.minusSeconds(30)
            val offset = ZonedDateTime.now().offset
            val record = HydrationRecord(
                startTime = inicio,
                startZoneOffset = offset,
                endTime = fim,
                endZoneOffset = offset,
                volume = Volume.milliliters(quantidadeMl.toDouble())
            )
            cliente.insertRecords(listOf(record))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Grava novo peso corporal (kg) no Health Connect.
     */
    suspend fun gravarPesoCorporal(context: Context, pesoKg: Double): Boolean = withContext(Dispatchers.IO) {
        val cliente = obterCliente(context) ?: return@withContext false
        try {
            val agora = Instant.now()
            val offset = ZonedDateTime.now().offset
            val record = WeightRecord(
                time = agora,
                zoneOffset = offset,
                weight = Mass.kilograms(pesoKg)
            )
            cliente.insertRecords(listOf(record))
            true
        } catch (_: Throwable) {
            false
        }
    }
}
