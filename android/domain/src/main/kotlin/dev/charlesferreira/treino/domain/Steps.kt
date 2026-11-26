package dev.charlesferreira.treino.domain

import kotlin.math.max
import kotlin.math.round

/** Passo do + e − da carga. [plates] vem preenchido quando o passo é "1 placa" (máquina). */
data class LoadStep(val kg: Double, val plates: Int? = null)

/**
 * Passo por equipamento. O `stepKg` do exercício sempre ganha.
 *
 * Padrões:
 * - halteres 2 kg, barra 2 kg (do plano);
 * - máquina 1 placa, com placa de 5 kg quando o exercício não diz outra (do plano);
 * - cabo 2,5 kg: as torres de polia costumam ir de 2,5 em 2,5, e é uma das opções do design;
 * - peso corporal 2 kg: o que se soma é lastro (anilha ou cinto), igual ao halter;
 * - outro 1 kg: o passo mais fino das opções do design, para não pular cargas que a pessoa usa.
 */
object Steps {
    const val DEFAULT_PLATE_KG = 5.0

    /** Opções do seletor "Passo do + e −" no design. */
    val KG_OPTIONS = listOf(1.0, 2.0, 2.5, 5.0)

    fun defaultFor(equipment: Equipment, plateKg: Double? = null): LoadStep = when (equipment) {
        Equipment.DUMBBELL -> LoadStep(2.0)
        Equipment.BARBELL -> LoadStep(2.0)
        Equipment.MACHINE -> LoadStep(plateKg ?: DEFAULT_PLATE_KG, plates = 1)
        Equipment.CABLE -> LoadStep(2.5)
        Equipment.BODYWEIGHT -> LoadStep(2.0)
        Equipment.OTHER -> LoadStep(1.0)
    }

    fun of(exercise: Exercise): LoadStep =
        exercise.stepKg?.takeIf { it > 0 }?.let { LoadStep(it) }
            ?: defaultFor(exercise.equipment, exercise.plateKg)

    fun up(weightKg: Double, exercise: Exercise): Double = clean(weightKg + of(exercise).kg)

    /** Nunca abaixo de zero. */
    fun down(weightKg: Double, exercise: Exercise): Double = clean(max(0.0, weightKg - of(exercise).kg))

    /** Tira o ruído de ponto flutuante (2,5 + 2,5 + 2,5...). */
    private fun clean(kg: Double): Double = round(kg * 1000) / 1000
}

/**
 * Equipamento deduzido do nome, para o catálogo e os exercícios vindos do PWA (que não tinham
 * esse campo). "halter" no nome é a regra do plano e vem antes de tudo; o resto é palpite que a
 * pessoa corrige na tela do exercício.
 */
object EquipmentGuess {
    private val machine = listOf(
        "maquina", "leg press", "cadeira", "mesa flexora", "flexora", "extensora", "peck deck",
        "smith", "hack", "voador", "graviton",
    )
    private val cable = listOf("polia", "cabo", "corda", "crossover", "face pull", "pulley", "puxada")
    private val bodyweight = listOf(
        "barra fixa", "flexao", "paralelas", "mergulho", "prancha", "elevacao de pernas", "burpee",
        "hollow", "abdominal supra", "abdominal infra",
    )
    private val barbell = listOf("barra", "levantamento terra", "terra sumo")

    fun isDumbbell(name: String): Boolean = "halter" in normalizeText(name)

    fun fromName(name: String, kind: ExerciseKind): Equipment {
        val n = normalizeText(name)
        return when {
            "halter" in n -> Equipment.DUMBBELL
            kind == ExerciseKind.TIME -> Equipment.OTHER
            machine.any { it in n } -> Equipment.MACHINE
            cable.any { it in n } -> Equipment.CABLE
            bodyweight.any { it in n } -> Equipment.BODYWEIGHT
            barbell.any { it in n } -> Equipment.BARBELL
            else -> Equipment.OTHER
        }
    }
}
