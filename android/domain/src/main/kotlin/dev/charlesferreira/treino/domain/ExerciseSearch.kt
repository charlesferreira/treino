package dev.charlesferreira.treino.domain

import java.text.Collator
import java.util.Locale

/** Grupos do catálogo, na ordem e com os rótulos do `GROUPS` do PWA (`src/exercises.ts`). */
object ExerciseGroups {
    const val MINE = "meus"

    val ALL: List<Pair<String, String>> = listOf(
        "peito" to "Peito",
        "costas" to "Costas",
        "ombros" to "Ombros",
        "biceps" to "Bíceps",
        "triceps" to "Tríceps",
        "antebraco" to "Antebraço",
        "quadriceps" to "Quadríceps",
        "posteriores" to "Posteriores",
        "gluteos" to "Glúteos",
        "panturrilha" to "Panturrilha",
        "abdomen" to "Abdômen",
        "cardio" to "Cardio",
    )

    /** Grupo desconhecido ou `meus` = "Meus exercícios", como o `groupLabel` do PWA. */
    fun label(group: String): String = ALL.firstOrNull { it.first == group }?.second ?: "Meus exercícios"

    /** Ordem do seletor: os meus primeiro, depois o catálogo. */
    val ORDER: List<String> = listOf(MINE) + ALL.map { it.first }
}

/** Porte do `searchExercises` do PWA: nome e sinônimos, sem acento nem caixa. */
object ExerciseSearch {
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply {
        strength = Collator.PRIMARY
    }

    /**
     * Vazio = todos, por nome. Com termo: primeiro os que começam com ele (no nome ou num
     * sinônimo), depois os que o contêm; empate pelo nome.
     */
    fun search(all: List<Exercise>, query: String): List<Exercise> {
        val q = normalizeText(query)
        if (q.isEmpty()) return all.sortedWith(compareBy(collator) { it.name })
        return all.mapNotNull { ex ->
            val hay = listOf(normalizeText(ex.name)) + ex.aliases.map(::normalizeText)
            when {
                hay.any { it.startsWith(q) } -> 0 to ex
                hay.any { q in it } -> 1 to ex
                else -> null
            }
        }.sortedWith(compareBy<Pair<Int, Exercise>> { it.first }.thenBy(collator) { it.second.name }).map { it.second }
    }

    /** Já existe um exercício com esse nome (sem acento nem caixa)? O "Criar" some nesse caso. */
    fun exactMatch(all: List<Exercise>, query: String): Exercise? {
        val q = normalizeText(query)
        if (q.isEmpty()) return null
        return all.firstOrNull { normalizeText(it.name) == q }
    }

    /** Agrupa na ordem de [ExerciseGroups.ORDER]; grupo fora da lista vai para os meus. */
    fun grouped(found: List<Exercise>): List<Pair<String, List<Exercise>>> {
        val byGroup = found.groupBy { if (it.group in ExerciseGroups.ORDER) it.group else ExerciseGroups.MINE }
        return ExerciseGroups.ORDER.mapNotNull { g -> byGroup[g]?.takeIf { it.isNotEmpty() }?.let { g to it } }
    }
}
