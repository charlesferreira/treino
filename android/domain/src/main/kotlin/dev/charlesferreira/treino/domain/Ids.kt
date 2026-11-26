package dev.charlesferreira.treino.domain

import java.text.Normalizer
import java.util.UUID

/**
 * UUIDs determinísticos (v3, `UUID.nameUUIDFromBytes`) a partir dos ids do PWA, para que
 * reimportar o mesmo backup grave por cima em vez de duplicar. Registros novos criados no app
 * usam [random].
 */
object Ids {
    fun exercise(legacyId: String): String = of("treino:exercise:$legacyId")

    fun workout(legacyId: String): String = of("treino:workout:$legacyId")

    fun workoutItem(workoutLegacyId: String, index: Int): String =
        of("treino:workout-item:$workoutLegacyId:$index")

    /** Uma sessão por dia no PWA: a data é a chave. */
    fun session(date: String): String = of("treino:session:$date")

    fun setEntry(date: String, exerciseLegacyId: String, index: Int): String =
        of("treino:set:$date:$exerciseLegacyId:$index")

    fun timeEntry(date: String, exerciseLegacyId: String, index: Int): String =
        of("treino:time:$date:$exerciseLegacyId:$index")

    fun random(): String = UUID.randomUUID().toString()

    private fun of(name: String): String = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString()
}

/** Minúsculas e sem acento, como `normalize` do PWA. */
fun normalizeText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** "remada-maluca_2" → "Remada maluca 2". Nome de um exercício citado sem definição. */
fun humanizeId(id: String): String {
    val words = id.replace('-', ' ').replace('_', ' ').trim().replace(Regex("\\s+"), " ")
    if (words.isEmpty()) return id
    return words.replaceFirstChar { it.titlecase() }
}
