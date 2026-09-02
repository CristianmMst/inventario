package co.inventario.feature.catalogo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.inventario.domain.modelo.LimitesImagen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.assertTrue

/** RNF-05: la foto del producto sale ≤ 300 KB y ≤ 1280 px del celular, antes de subir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompresorImagenTest {

    /** Ruido aleatorio: lo peor para JPEG, así el límite de bytes de verdad se ejercita. */
    private fun fotoRuidosa(ancho: Int, alto: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val pixeles = IntArray(ancho * alto) { Random(it).nextInt() or 0xFF000000.toInt() }
        bitmap.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    @Test
    fun `rnf 05 una foto de 4000x3000 queda dentro de 1280 px y 300 KB`() {
        val original = fotoRuidosa(4000, 3000)
        assertTrue(original.size > LimitesImagen.PRODUCTO.bytesMaximos, "la original debe exceder el límite para que el test signifique algo")

        val comprimida = CompresorImagen.comprimir(original, LimitesImagen.PRODUCTO)

        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(comprimida, 0, comprimida.size, opciones)
        assertTrue(maxOf(opciones.outWidth, opciones.outHeight) <= 1280, "lado mayor ${opciones.outWidth}x${opciones.outHeight}")
        assertTrue(comprimida.size <= 300 * 1024, "pesa ${comprimida.size} bytes")
        assertTrue(opciones.outMimeType == "image/jpeg")
    }

    @Test
    fun `una foto pequena no se agranda`() {
        val original = fotoRuidosa(640, 480)
        val comprimida = CompresorImagen.comprimir(original, LimitesImagen.PRODUCTO)
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(comprimida, 0, comprimida.size, opciones)
        assertTrue(opciones.outWidth <= 640 && opciones.outHeight <= 480)
    }
}
