package co.inventario.feature.facturas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.di.AmbitoAplicacion
import co.inventario.data.repositorio.FacturaNueva
import co.inventario.data.repositorio.RepositorioFacturas
import co.inventario.data.repositorio.ResultadoFactura
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.regla.cuadraFactura
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class FacturaUiState(
    val proveedorId: String? = null,
    val numero: String = "",
    val fechaEmision: String = LocalDate.now().toString(),
    val fechaVencimiento: String = "",
    val moneda: String,
    val tasaCambio: String = "",
    val base: String = "",
    val impuesto: String = "",
    val total: String = "",
    val notas: String = "",
    val recepciones: List<String> = emptyList(),
    val fotos: List<ByteArray> = emptyList(),
    /** RN-18: diferencia total − (base + impuesto) mientras se teclea; null si cuadra o falta algo. */
    val diferenciaCuadre: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val pendiente: String? = null,
)

sealed interface FacturaSuceso {
    data class Registrada(val facturaId: String) : FacturaSuceso
}

/**
 * RF-FAC-001 / RF-FAC-003 / RN-18 / RF-FAC-005: registro de factura de compra. El cuadre se
 * valida en el celular antes de enviar; las fotos (ya comprimidas, RNF-05) se suben después
 * en el ámbito de aplicación sin retener la pantalla.
 */
@HiltViewModel(assistedFactory = FacturaViewModel.Fabrica::class)
class FacturaViewModel @AssistedInject constructor(
    private val facturas: RepositorioFacturas,
    @AmbitoAplicacion private val ambitoSubida: CoroutineScope,
    @Assisted private val monedaBase: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(monedaBase: String): FacturaViewModel
    }

    private val _estado = MutableStateFlow(FacturaUiState(moneda = monedaBase))
    val estado: StateFlow<FacturaUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<FacturaSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    fun elegirProveedor(id: String) = _estado.update { it.copy(proveedorId = id, erroresCampo = it.erroresCampo - "proveedor") }
    fun cambiarNumero(v: String) = _estado.update { it.copy(numero = v, erroresCampo = it.erroresCampo - "numero") }
    fun cambiarFechaEmision(v: String) = _estado.update { it.copy(fechaEmision = v, erroresCampo = it.erroresCampo - "fechaEmision") }
    fun cambiarFechaVencimiento(v: String) = _estado.update { it.copy(fechaVencimiento = v, erroresCampo = it.erroresCampo - "fechaVencimiento") }
    fun cambiarMoneda(v: String) = _estado.update { it.copy(moneda = v.uppercase().take(3), erroresCampo = it.erroresCampo - "tasaCambio") }
    fun cambiarTasa(v: String) = _estado.update { it.copy(tasaCambio = v, erroresCampo = it.erroresCampo - "tasaCambio") }
    fun cambiarBase(v: String) = _estado.update { recalcular(it.copy(base = v, erroresCampo = it.erroresCampo - "total")) }
    fun cambiarImpuesto(v: String) = _estado.update { recalcular(it.copy(impuesto = v, erroresCampo = it.erroresCampo - "total")) }
    fun cambiarTotal(v: String) = _estado.update { recalcular(it.copy(total = v, erroresCampo = it.erroresCampo - "total")) }
    fun cambiarNotas(v: String) = _estado.update { it.copy(notas = v) }
    fun alternarRecepcion(id: String) = _estado.update { it.copy(recepciones = if (id in it.recepciones) it.recepciones - id else it.recepciones + id) }
    fun adjuntarFoto(jpeg: ByteArray) = _estado.update { it.copy(fotos = it.fotos + jpeg) }
    fun quitarFoto(indice: Int) = _estado.update { it.copy(fotos = it.fotos.filterIndexed { i, _ -> i != indice }) }

    private fun recalcular(s: FacturaUiState): FacturaUiState {
        val moneda = Moneda(s.moneda.ifBlank { monedaBase })
        val cuadre = runCatching {
            cuadraFactura(Dinero.desde(s.base.trim(), moneda), Dinero.desde(s.impuesto.trim(), moneda), Dinero.desde(s.total.trim(), moneda))
        }.getOrNull()
        return s.copy(diferenciaCuadre = cuadre?.takeIf { !it.cuadra }?.diferencia?.aApi()?.monto)
    }

    fun guardar() {
        val s = _estado.value
        if (s.cargando) return
        val errores = validar(s)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val datos = FacturaNueva(
                proveedorId = s.proveedorId!!, numero = s.numero, fechaEmision = LocalDate.parse(s.fechaEmision.trim()),
                fechaVencimiento = s.fechaVencimiento.trim().ifBlank { null }?.let(LocalDate::parse), moneda = s.moneda,
                tasaCambio = s.tasaCambio.trim().ifBlank { null }, baseGravable = s.base.trim(), impuesto = s.impuesto.trim(), total = s.total.trim(),
                notas = s.notas.ifBlank { null }, recepciones = s.recepciones,
            )
            when (val r = facturas.registrar(datos)) {
                is ResultadoFactura.Confirmada -> {
                    _estado.update { it.copy(cargando = false, pendiente = null) }
                    _sucesos.send(FacturaSuceso.Registrada(r.factura.id))
                    // RF-FAC-005: una o varias imágenes; van después, sin bloquear.
                    s.fotos.forEach { foto -> ambitoSubida.launch { facturas.adjuntarImagen(r.factura.id, foto) } }
                }
                is ResultadoFactura.Pendiente -> _estado.update { it.copy(cargando = false, pendiente = r.clave, error = r.error.mensaje) }
                is ResultadoFactura.Rechazada -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }

    private fun validar(s: FacturaUiState): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (s.proveedorId == null) errores["proveedor"] = "Elige el proveedor."
        if (s.numero.isBlank()) errores["numero"] = "El número de la factura es obligatorio."
        if (!fechaValida(s.fechaEmision)) errores["fechaEmision"] = "Fecha en formato AAAA-MM-DD."
        if (s.fechaVencimiento.isNotBlank() && !fechaValida(s.fechaVencimiento)) errores["fechaVencimiento"] = "Fecha en formato AAAA-MM-DD."
        if (!Regex("^[A-Z]{3}$").matches(s.moneda)) errores["moneda"] = "Moneda de tres letras, como COP."
        if (s.moneda != monedaBase && s.tasaCambio.trim().toBigDecimalOrNull()?.let { it.signum() > 0 } != true) {
            errores["tasaCambio"] = "En otra moneda hace falta la tasa de cambio a $monedaBase."
        }
        val montos = listOf("base" to s.base, "impuesto" to s.impuesto, "total" to s.total)
        montos.forEach { (campo, valor) -> if (valor.trim().toBigDecimalOrNull() == null) errores[campo] = "Escribe el monto." }
        if (montos.none { it.first in errores } && s.diferenciaCuadre != null) {
            errores["total"] = "Base más impuesto no da el total: hay una diferencia de ${s.diferenciaCuadre}."
        }
        return errores
    }

    private fun fechaValida(texto: String): Boolean = try {
        LocalDate.parse(texto.trim()); true
    } catch (_: DateTimeParseException) {
        false
    }
}

/** Para la pantalla: los proveedores se cargan aparte, con el repositorio de compras. */
data class ProveedorOpcion(val id: String, val nombre: String)

fun <T> Resultado<T>.valorONull(): T? = (this as? Resultado.Exito)?.valor
