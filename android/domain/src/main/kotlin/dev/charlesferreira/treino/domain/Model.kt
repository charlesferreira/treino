package dev.charlesferreira.treino.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Modelo do app nativo. Cada classe espelha uma tabela Room (Fase 1): `exercise`, `workout`,
 * `workout_item`, `session`, `set_entry` e `time_entry`. Ids são UUIDs em texto; os dados
 * importados do PWA guardam o id antigo em `legacyId` e têm UUIDs determinísticos (ver [Ids]).
 *
 * As classes também são o bloco `native` do backup format 3 (ver BackupExporter), por isso
 * são @Serializable. Não existe RIR em lugar nenhum.
 */

/** Como o exercício é registrado. */
@Serializable
enum class ExerciseKind {
    /** Carga × reps. */
    @SerialName("load") LOAD,

    /** Minutos (e distância opcional). */
    @SerialName("time") TIME,
}

@Serializable
enum class Equipment {
    @SerialName("dumbbell") DUMBBELL,
    @SerialName("barbell") BARBELL,
    @SerialName("machine") MACHINE,
    @SerialName("cable") CABLE,
    @SerialName("bodyweight") BODYWEIGHT,
    @SerialName("other") OTHER,
}

@Serializable
data class Exercise(
    val id: String,
    val legacyId: String? = null,
    val name: String,
    /** Grupo do catálogo do PWA (`peito`, `costas`...) ou `meus`. */
    val group: String,
    val kind: ExerciseKind = ExerciseKind.LOAD,
    val equipment: Equipment = Equipment.OTHER,
    /** A carga é anotada por halter e o volume conta ×2. */
    val countsTwoDumbbells: Boolean = false,
    /** Passo do + e − escolhido pela pessoa. Nulo = padrão do equipamento (ver [Steps]). */
    val stepKg: Double? = null,
    /** Peso da placa, só para máquina. Nulo = [Steps.DEFAULT_PLATE_KG]. */
    val plateKg: Double? = null,
    val aliases: List<String> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class Workout(
    val id: String,
    val legacyId: String? = null,
    val name: String,
    /** Ordem na sequência circular, a partir de 0. */
    val position: Int,
)

@Serializable
data class WorkoutItem(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val minutesMin: Double? = null,
    val minutesMax: Double? = null,
    val restSec: Int = 0,
    val notes: String? = null,
)

@Serializable
data class Session(
    val id: String,
    /** "YYYY-MM-DD", no fuso local. */
    val date: String,
    val workoutId: String? = null,
    val note: String? = null,
    /** Epoch ms. Nulo nos dias importados do PWA. */
    val startedAt: Long? = null,
    val endedAt: Long? = null,
)

@Serializable
data class SetEntry(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** Ordem da série dentro do exercício nesta sessão, a partir de 0. */
    val position: Int,
    /** Por halter quando o exercício conta 2 halteres. */
    val weightKg: Double,
    val reps: Int,
    /** Epoch ms de quando a série foi registrada. Nulo nos dias importados. */
    val loggedAt: Long? = null,
)

@Serializable
data class TimeEntry(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** Ordem dentro do exercício nesta sessão, a partir de 0. */
    val position: Int,
    val minutes: Double,
    val km: Double? = null,
    val place: String? = null,
    val loggedAt: Long? = null,
)
