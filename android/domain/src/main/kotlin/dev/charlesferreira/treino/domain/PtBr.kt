package dev.charlesferreira.treino.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

/** Formatação dos fatos que as telas mostram, em pt-BR. */
object PtBr {
    private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val WEEKDAYS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

    /** "2026-11-26" → "26 nov"; com [padDay], "01 out" (coluna da revisão de cargas). */
    fun shortDate(date: String, padDay: Boolean = false): String {
        val d = LocalDate.parse(date)
        val day = if (padDay) d.dayOfMonth.toString().padStart(2, '0') else d.dayOfMonth.toString()
        return "$day ${MONTHS[d.monthValue - 1]}"
    }

    /** "19 nov, quinta" (título do dia no histórico). */
    fun dayTitle(date: String): String {
        val d = LocalDate.parse(date)
        return "${shortDate(date)}, ${WEEKDAYS[d.dayOfWeek.value - 1]}"
    }

    /** "16", "2,5", "6.840": até 1 casa decimal, vírgula, ponto de milhar. */
    fun number(value: Double): String {
        val tenths = round(value * 10).toLong()
        val negative = tenths < 0
        val absTenths = abs(tenths)
        val whole = (absTenths / 10).toString().reversed().chunked(3).joinToString(".").reversed()
        val frac = absTenths % 10
        return (if (negative) "-" else "") + whole + (if (frac != 0L) ",$frac" else "")
    }

    fun kg(value: Double): String = "${number(value)} kg"

    /** "+2 kg", "−2,5 kg"; máquina com placa que divide a diferença: "+1 placa", "+2 placas". */
    fun loadDelta(deltaKg: Double, exercise: Exercise? = null): String {
        val sign = if (deltaKg < 0) "−" else "+"
        if (exercise?.equipment == Equipment.MACHINE && exercise.stepKg == null) {
            val plate = exercise.plateKg ?: Steps.DEFAULT_PLATE_KG
            val plates = abs(deltaKg) / plate
            if (plate > 0 && plates >= 1 && abs(plates - round(plates)) < 1e-9) {
                val n = round(plates).toInt()
                return "$sign$n ${if (n == 1) "placa" else "placas"}"
            }
        }
        return "$sign${kg(abs(deltaKg))}"
    }

    /** "+2 kg em 26 nov". */
    fun increase(increase: LoadIncrease, exercise: Exercise? = null): String =
        "${loadDelta(increase.deltaKg, exercise)} em ${shortDate(increase.date)}"

    /** Unidade explícita no histórico. */
    fun loadUnit(exercise: Exercise): String = if (exercise.countsTwoDumbbells) "kg por halter" else "kg"
}
