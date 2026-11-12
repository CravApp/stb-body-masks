package com.stb.bodymasks.mask

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

data class LoadedMask(
    val definition: MaskDefinition,
    val bitmaps: Map<String, Bitmap>,
)

class MaskRepository(private val context: Context) {
    fun loadAll(): List<LoadedMask> {
        val names = context.assets.list("masks")?.sorted().orEmpty()
        val loaded = ArrayList<LoadedMask>()
        for (folder in names) {
            try {
                loaded += load(folder)
            } catch (error: Throwable) {
                Log.e(TAG, "No se pudo cargar la máscara $folder", error)
            }
        }
        return loaded.sortedBy { it.definition.name }
    }

    private fun load(folder: String): LoadedMask {
        val text = context.assets.open("masks/$folder/rig.json").bufferedReader().use { it.readText() }
        val definition = MaskFormat.parse(text)
        if (definition.id != folder) {
            throw MaskFormatException("El id «${definition.id}» no coincide con la carpeta $folder")
        }
        val cache = HashMap<String, Bitmap>()
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmaps = definition.parts.associate { part ->
            val bitmap = cache.getOrPut(part.src) {
                context.assets.open("masks/$folder/${part.src}").use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                        ?: throw MaskFormatException("Imagen inválida: ${part.src}")
                }
            }
            part.id to bitmap
        }
        return LoadedMask(definition, bitmaps)
    }

    private companion object {
        const val TAG = "BodyMasks"
    }
}
