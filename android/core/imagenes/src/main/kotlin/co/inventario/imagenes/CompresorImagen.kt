package co.inventario.imagenes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.inventario.domain.modelo.LimitesImagen
import java.io.ByteArrayOutputStream

/**
 * RNF-05: reduce la foto en el celular antes de subirla. Primero el tamaño (lado mayor ≤ límite,
 * sin agrandar), luego la calidad JPEG en pasos hasta caber en los bytes máximos; si ni con la
 * calidad mínima cabe, reduce el lado y repite. El servidor vuelve a comprobar los límites.
 */
object CompresorImagen {

    fun comprimir(original: ByteArray, limites: LimitesImagen): ByteArray {
        val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bordes)
        val ladoOriginal = maxOf(bordes.outWidth, bordes.outHeight)
        require(ladoOriginal > 0) { "El archivo no es una imagen." }

        // Muestreo en potencias de dos al decodificar: evita cargar en memoria una foto de 12 MP.
        var muestreo = 1
        while (ladoOriginal / (muestreo * 2) >= limites.ladoMaximoPx) muestreo *= 2
        val decodificado = BitmapFactory.decodeByteArray(original, 0, original.size, BitmapFactory.Options().apply { inSampleSize = muestreo })
            ?: throw IllegalArgumentException("El archivo no es una imagen.")

        var ladoObjetivo = minOf(limites.ladoMaximoPx, maxOf(decodificado.width, decodificado.height))
        var resultado: ByteArray
        while (true) {
            val escalado = escalar(decodificado, ladoObjetivo)
            resultado = aJpegQueCabe(escalado, limites)
            if (escalado !== decodificado) escalado.recycle()
            if (resultado.size <= limites.bytesMaximos || ladoObjetivo <= LADO_MINIMO) break
            ladoObjetivo = (ladoObjetivo * 0.8).toInt().coerceAtLeast(LADO_MINIMO)
        }
        decodificado.recycle()
        return resultado
    }

    private fun escalar(bitmap: Bitmap, ladoMaximo: Int): Bitmap {
        val ladoActual = maxOf(bitmap.width, bitmap.height)
        if (ladoActual <= ladoMaximo) return bitmap
        val factor = ladoMaximo.toDouble() / ladoActual
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt().coerceAtLeast(1), (bitmap.height * factor).toInt().coerceAtLeast(1), true)
    }

    private fun aJpegQueCabe(bitmap: Bitmap, limites: LimitesImagen): ByteArray {
        var calidad = limites.calidadInicial
        while (true) {
            val salida = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
            val bytes = salida.toByteArray()
            if (bytes.size <= limites.bytesMaximos || calidad <= CALIDAD_MINIMA) return bytes
            calidad -= PASO_CALIDAD
        }
    }

    private const val LADO_MINIMO = 480
    private const val CALIDAD_MINIMA = 40
    private const val PASO_CALIDAD = 10
}
