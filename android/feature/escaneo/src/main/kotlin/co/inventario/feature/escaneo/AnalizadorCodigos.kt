package co.inventario.feature.escaneo

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analiza cada fotograma con ML Kit (modelo empaquetado) y entrega los códigos que pasan el
 * antirrebote. Se cierra con la pantalla para liberar la cámara y el modelo (RNF-10).
 */
class AnalizadorCodigos(
    private val antirrebote: AntirreboteLecturas = AntirreboteLecturas(),
    private val alLeer: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val lector: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE,
            )
            .build(),
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imagen: ImageProxy) {
        val media = imagen.image
        if (media == null) {
            imagen.close()
            return
        }
        val entrada = InputImage.fromMediaImage(media, imagen.imageInfo.rotationDegrees)
        lector.process(entrada)
            .addOnSuccessListener { codigos ->
                codigos.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                    ?.let { if (antirrebote.aceptar(it)) alLeer(it) }
            }
            .addOnCompleteListener { imagen.close() }
    }

    fun cerrar() = lector.close()
}
