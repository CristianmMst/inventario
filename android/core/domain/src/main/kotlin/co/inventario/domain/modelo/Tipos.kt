package co.inventario.domain.modelo

import co.inventario.domain.regla.ReglaViolada
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Tipos de valor del dominio, espejo de `app/dominio/tipos.py` del backend.
 * Dinero y cantidades son BigDecimal exacto: nunca Double (constitution.md §2, E-01, RN-07).
 */

private const val DECIMALES_DINERO = 4
private const val DECIMALES_CANTIDAD = 3
private val ISO_4217 = Regex("^[A-Z]{3}$")

private fun decimalOrNull(texto: String): BigDecimal? =
    try {
        BigDecimal(texto.trim())
    } catch (_: NumberFormatException) {
        null
    }

@JvmInline
value class Moneda(val codigo: String) {
    init {
        if (!ISO_4217.matches(codigo)) {
            throw ReglaViolada(
                "MONEDA_INVALIDA",
                "La moneda debe ser un código ISO 4217 de tres letras mayúsculas, como COP.",
            )
        }
    }

    override fun toString(): String = codigo
}

data class DineroApi(val monto: String, val moneda: String)

/** Cantidad de stock: puede ser cero o negativa (RN-04); máximo 3 decimales. */
data class Cantidad(val valor: BigDecimal) : Comparable<Cantidad> {
    init {
        if (valor.stripTrailingZeros().scale() > DECIMALES_CANTIDAD) {
            throw ReglaViolada(
                "CANTIDAD_INVALIDA",
                "La cantidad admite como mucho $DECIMALES_CANTIDAD decimales.",
            )
        }
    }

    val normalizada: BigDecimal get() = valor.setScale(DECIMALES_CANTIDAD, RoundingMode.UNNECESSARY)

    fun aApi(): String = normalizada.toPlainString()

    fun esPositiva(): Boolean = valor.signum() > 0

    fun decimales(): Int = maxOf(0, valor.stripTrailingZeros().scale())

    operator fun plus(otra: Cantidad): Cantidad = Cantidad(valor + otra.valor)
    operator fun minus(otra: Cantidad): Cantidad = Cantidad(valor - otra.valor)
    operator fun unaryMinus(): Cantidad = Cantidad(valor.negate())
    override fun compareTo(other: Cantidad): Int = valor.compareTo(other.valor)
    override fun equals(other: Any?): Boolean = other is Cantidad && normalizada == other.normalizada
    override fun hashCode(): Int = normalizada.hashCode()

    companion object {
        val CERO = Cantidad(BigDecimal.ZERO)

        fun desde(texto: String): Cantidad =
            Cantidad(decimalOrNull(texto) ?: throw ReglaViolada("CANTIDAD_INVALIDA", "La cantidad no es un número válido."))
    }
}

data class Dinero(val monto: BigDecimal, val moneda: Moneda) {
    init {
        if (monto.stripTrailingZeros().scale() > DECIMALES_DINERO) {
            throw ReglaViolada("MONTO_INVALIDO", "El monto admite como mucho $DECIMALES_DINERO decimales.")
        }
    }

    val normalizado: BigDecimal get() = monto.setScale(DECIMALES_DINERO, RoundingMode.UNNECESSARY)

    fun aApi(): DineroApi = DineroApi(normalizado.toPlainString(), moneda.codigo)

    private fun mismaMoneda(otro: Dinero) {
        if (moneda != otro.moneda) {
            throw ReglaViolada(
                "MONEDAS_DISTINTAS",
                "No se pueden operar montos en monedas distintas sin una tasa de cambio.",
            )
        }
    }

    operator fun plus(otro: Dinero): Dinero {
        mismaMoneda(otro)
        return Dinero(monto + otro.monto, moneda)
    }

    operator fun minus(otro: Dinero): Dinero {
        mismaMoneda(otro)
        return Dinero(monto - otro.monto, moneda)
    }

    operator fun times(cantidad: Cantidad): Dinero =
        Dinero((monto * cantidad.valor).setScale(DECIMALES_DINERO, RoundingMode.HALF_UP), moneda)

    override fun equals(other: Any?): Boolean =
        other is Dinero && moneda == other.moneda && normalizado == other.normalizado

    override fun hashCode(): Int = 31 * normalizado.hashCode() + moneda.hashCode()

    companion object {
        fun desde(monto: String, moneda: Moneda): Dinero =
            Dinero(decimalOrNull(monto) ?: throw ReglaViolada("MONTO_INVALIDO", "El monto no es un número válido."), moneda)

        fun desdeApi(api: DineroApi): Dinero = desde(api.monto, Moneda(api.moneda))
    }
}

enum class TipoUnidad(val codigo: String) {
    DISCRETA("discreta"),
    CONTINUA("continua"),
    ;

    companion object {
        fun desde(codigo: String): TipoUnidad = entries.first { it.codigo == codigo }
    }
}

/** RF-CAT-004: la unidad declara si es discreta o continua y cuántos decimales admite. */
data class UnidadMedida(val codigo: String, val nombre: String, val tipo: TipoUnidad, val decimales: Int) {
    init {
        if (tipo == TipoUnidad.DISCRETA && decimales != 0) {
            throw ReglaViolada("UNIDAD_INVALIDA", "Una unidad discreta no admite decimales.")
        }
        if (decimales !in 0..DECIMALES_CANTIDAD) {
            throw ReglaViolada("UNIDAD_INVALIDA", "Los decimales de una unidad van de 0 a $DECIMALES_CANTIDAD.")
        }
    }
}
