package co.inventario.designsystem.componentes

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Cómo se **lee** el dinero y las cantidades. Nada de esto toca el dato: los importes siguen
 * viajando como cadena decimal con cuatro decimales (enmienda E-01) y las cantidades como texto
 * (RN-07). Esto es solo la capa de lectura.
 *
 * Hacía falta porque los importes se pintaban tal cual llegaban: «66129.2424 COP» en la ficha,
 * que a un metro y de pie no se lee como sesenta y seis mil. RNF-09 pide legibilidad, y un
 * número sin separador de miles no la tiene.
 */
object Formato {

    /**
     * Español, no la configuración del teléfono. La app es en español por constitución (§9) y no
     * tiene traducciones: si el teléfono está en inglés, los números salían con la coma y el punto
     * al revés («66,129.24» en vez de «66.129,24») dentro de una interfaz en castellano.
     */
    private val ESPANOL: Locale = Locale.forLanguageTag("es")

    /**
     * Importe con separador de miles y solo los decimales que de verdad tiene. Un precio redondo
     * sale «66.129»; un costo por unidad que cae bajo la unidad mínima de la moneda —el tornillo
     * a 12,50 COP que motivó E-01— conserva sus decimales.
     */
    fun monto(monto: String, moneda: String? = null, locale: Locale = ESPANOL): String {
        val valor = monto.toBigDecimalOrNull() ?: return listOfNotNull(monto, moneda).joinToString(" ")
        val simbolos = DecimalFormatSymbols.getInstance(locale)
        val formato = DecimalFormat("#,##0.####", simbolos).apply { isGroupingUsed = true }
        val texto = formato.format(valor.stripTrailingZeros())
        return if (moneda != null) "$texto $moneda" else texto
    }

    /** Una cantidad de inventario: sin ceros de adorno y con miles cuando los hay. */
    fun cantidad(valor: BigDecimal, locale: Locale = ESPANOL): String {
        val simbolos = DecimalFormatSymbols.getInstance(locale)
        return DecimalFormat("#,##0.###", simbolos).format(valor.stripTrailingZeros())
    }

    /** La misma cantidad, cuando ya viene como texto de la API. */
    fun cantidad(valor: String, locale: Locale = ESPANOL): String =
        valor.toBigDecimalOrNull()?.let { cantidad(it, locale) } ?: valor
}
