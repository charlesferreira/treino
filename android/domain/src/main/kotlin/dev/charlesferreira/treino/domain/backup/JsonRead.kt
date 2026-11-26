package dev.charlesferreira.treino.domain.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/* Leitura tolerante de JsonElement: tipo errado vira nulo, nunca exceção. */

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asString(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull || !p.isString) return null
    return p.content
}

/** Número, ou texto numérico ("16", "2,5"). */
internal fun JsonElement?.asDouble(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val d = if (p.isString) p.content.trim().replace(',', '.').toDoubleOrNull() else p.doubleOrNull
    return d?.takeIf { it.isFinite() }
}

internal fun JsonElement?.asInt(): Int? = asDouble()?.let { Math.round(it).toInt() }

internal fun JsonElement?.asBoolean(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull || p.isString) return null
    return p.booleanOrNull
}

/** `[min, max]` do PWA. */
internal fun JsonElement?.asRange(): Pair<Double, Double>? {
    val a = asArray() ?: return null
    val min = a.getOrNull(0).asDouble() ?: return null
    val max = a.getOrNull(1).asDouble() ?: min
    return min to max
}

/** Número inteiro sai sem ".0", como o PWA escreve. */
internal fun jsonNumber(d: Double): JsonPrimitive =
    if (d == Math.floor(d) && Math.abs(d) < 1e15) JsonPrimitive(d.toLong()) else JsonPrimitive(d)
