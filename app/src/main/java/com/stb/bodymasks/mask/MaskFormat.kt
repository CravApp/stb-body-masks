package com.stb.bodymasks.mask

import com.stb.bodymasks.pose.PosePoint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MaskFormatException(message: String) : IllegalArgumentException(message)

data class MaskDefinition(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val parts: List<MaskPart>,
)

data class MaskPart(
    val id: String,
    val src: String,
    val zIndex: Int,
    val anchor: PosePoint,
    val aimFrom: PosePoint,
    val aimTo: PosePoint,
    val sizeFrom: PosePoint,
    val sizeTo: PosePoint,
    val pivotX: Float,
    val pivotY: Float,
    val lengthScale: Float,
    val widthScale: Float,
    val minVisibility: Float,
    val flipHorizontal: Boolean,
    val opacity: Float,
)

object MaskFormat {
    private val idPattern = Regex("^[a-z0-9][a-z0-9_-]{0,40}$")

    fun parse(text: String): MaskDefinition {
        val root = try {
            kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            throw MaskFormatException("El rig.json no es JSON válido")
        }
        val version = root.requireFloat("formatVersion").toInt()
        if (version != 1) {
            throw MaskFormatException("formatVersion no soportada: $version")
        }
        val id = root.requireString("id")
        if (!idPattern.matches(id)) {
            throw MaskFormatException("id de máscara no válido: $id")
        }
        val name = root.requireString("name")
        if (name.isBlank()) throw MaskFormatException("El nombre de la máscara está vacío")
        val description = root.optionalString("description") ?: ""
        val defaultVisibility = root["defaults"]?.asObject()?.optionalFloat("minVisibility") ?: 0.5f
        if (defaultVisibility !in 0f..1f) {
            throw MaskFormatException("minVisibility fuera de rango")
        }
        val partsElement = root["parts"] ?: throw MaskFormatException("Falta parts")
        val partsArray = partsElement as? kotlinx.serialization.json.JsonArray
            ?: throw MaskFormatException("parts debe ser una lista")
        if (partsArray.isEmpty()) throw MaskFormatException("La máscara no tiene piezas")
        val parts = partsArray.map { element ->
            parsePart(element.asObject(), defaultVisibility)
        }
        if (parts.distinctBy { it.id }.size != parts.size) {
            throw MaskFormatException("Hay piezas con el mismo id")
        }
        return MaskDefinition(version, id, name, description, parts)
    }

    private fun parsePart(obj: JsonObject, defaultVisibility: Float): MaskPart {
        val id = obj.requireString("id")
        if (!idPattern.matches(id)) throw MaskFormatException("id de pieza no válido: $id")
        val src = obj.requireString("src")
        if (src.isBlank() || src.contains("..") || src.startsWith("/") || src.contains('\\') || src.contains(':')) {
            throw MaskFormatException("Ruta de imagen no permitida: $src")
        }
        val pivot = obj["pivot"]?.asObject() ?: throw MaskFormatException("Falta pivot en $id")
        val pivotX = pivot.requireFloat("x")
        val pivotY = pivot.requireFloat("y")
        if (pivotX !in -0.25f..1.25f || pivotY !in -0.25f..1.25f) {
            throw MaskFormatException("pivot fuera de rango en $id")
        }
        val lengthScale = obj.requireFloat("lengthScale")
        val widthScale = obj.requireFloat("widthScale")
        if (lengthScale !in 0.05f..8f || widthScale !in 0.05f..8f) {
            throw MaskFormatException("escala fuera de rango en $id")
        }
        val minVisibility = obj.optionalFloat("minVisibility") ?: defaultVisibility
        if (minVisibility !in 0f..1f) throw MaskFormatException("minVisibility fuera de rango en $id")
        val opacity = obj.optionalFloat("opacity") ?: 1f
        if (opacity !in 0f..1f) throw MaskFormatException("opacity fuera de rango en $id")
        return MaskPart(
            id = id,
            src = src,
            zIndex = obj.requireFloat("zIndex").toInt(),
            anchor = point(obj.requireString("anchor")),
            aimFrom = point(obj.requireString("aimFrom")),
            aimTo = point(obj.requireString("aimTo")),
            sizeFrom = point(obj.requireString("sizeFrom")),
            sizeTo = point(obj.requireString("sizeTo")),
            pivotX = pivotX,
            pivotY = pivotY,
            lengthScale = lengthScale,
            widthScale = widthScale,
            minVisibility = minVisibility,
            flipHorizontal = obj.optionalBoolean("flipHorizontal") ?: false,
            opacity = opacity,
        )
    }

    private fun point(name: String): PosePoint = try {
        PosePoint.fromName(name)
    } catch (error: IllegalArgumentException) {
        throw MaskFormatException("Punto desconocido: $name")
    }
}

private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject = try {
    jsonObject
} catch (error: IllegalArgumentException) {
    throw MaskFormatException("Se esperaba un objeto JSON")
}

private fun JsonObject.requireString(key: String): String {
    val element = this[key] ?: throw MaskFormatException("Falta «$key»")
    val primitive = element as? JsonPrimitive ?: throw MaskFormatException("«$key» debe ser texto")
    if (!primitive.isString) throw MaskFormatException("«$key» debe ser texto")
    return primitive.contentOrNull ?: throw MaskFormatException("«$key» debe ser texto")
}

private fun JsonObject.optionalString(key: String): String? {
    val element = this[key] ?: return null
    val primitive = element as? JsonPrimitive ?: throw MaskFormatException("«$key» debe ser texto")
    return primitive.contentOrNull
}

private fun JsonObject.requireFloat(key: String): Float {
    val element = this[key] ?: throw MaskFormatException("Falta «$key»")
    val primitive = element as? JsonPrimitive ?: throw MaskFormatException("«$key» debe ser un número")
    return primitive.floatOrNull ?: throw MaskFormatException("«$key» debe ser un número")
}

private fun JsonObject.optionalFloat(key: String): Float? {
    val element = this[key] ?: return null
    val primitive = element as? JsonPrimitive ?: throw MaskFormatException("«$key» debe ser un número")
    return primitive.floatOrNull ?: throw MaskFormatException("«$key» debe ser un número")
}

private fun JsonObject.optionalBoolean(key: String): Boolean? {
    val element = this[key] ?: return null
    val primitive = element as? JsonPrimitive ?: throw MaskFormatException("«$key» debe ser booleano")
    return primitive.booleanOrNull ?: throw MaskFormatException("«$key» debe ser booleano")
}
