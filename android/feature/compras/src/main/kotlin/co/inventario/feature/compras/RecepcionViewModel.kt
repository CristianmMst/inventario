package co.inventario.feature.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.LineaRecepcionNueva
import co.inventario.data.repositorio.RecepcionNueva
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.data.repositorio.ResultadoConfirmacion
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.Recepcion
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LineaRecepcionForm(val producto: ProductoBreve, val cantidad: String, val costo: String, val pendiente: String? = null)

/** RF-COM-009: el servidor detectó exceso; la usuaria decide explícitamente. */
data class AvisoExceso(val ordenNumero: String?, val lineasConExceso: Int)

data class RecepcionUiState(
    val ordenId: String? = null,
    val ordenNumero: String? = null,
    val proveedores: List<Proveedor> = emptyList(),
    val proveedorId: String? = null,
    val moneda: String,
    val tasaCambio: String = "",
    val notas: String = "",
    val lineas: List<LineaRecepcionForm> = emptyList(),
    /** Borrador ya creado en el servidor; se reutiliza al reintentar la confirmación. */
    val borradorId: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val avisoExceso: AvisoExceso? = null,
    val pendiente: String? = null,
)

sealed interface RecepcionSuceso {
    data class Confirmada(val recepcion: Recepcion) : RecepcionSuceso
}

/**
 * RF-COM-004 / RF-COM-005 / RF-COM-006 / RF-COM-009: recepción directa (sin orden) o contra
 * orden con las líneas precargadas con lo pendiente. Confirmar es atómico en el servidor y pasa
 * por la bandeja; el exceso solo entra tras confirmación explícita.
 */
@HiltViewModel(assistedFactory = RecepcionViewModel.Fabrica::class)
class RecepcionViewModel @AssistedInject constructor(
    private val compras: RepositorioCompras,
    @Assisted("ordenId") private val ordenId: String?,
    @Assisted("monedaBase") monedaBase: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(@Assisted("ordenId") ordenId: String?, @Assisted("monedaBase") monedaBase: String): RecepcionViewModel
    }

    private val _estado = MutableStateFlow(RecepcionUiState(ordenId = ordenId, moneda = monedaBase))
    val estado: StateFlow<RecepcionUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<RecepcionSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    init {
        viewModelScope.launch {
            (compras.proveedores() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(proveedores = r.valor) } }
            if (ordenId != null) {
                when (val r = compras.orden(ordenId)) {
                    is Resultado.Exito -> _estado.update {
                        val o = r.valor
                        it.copy(
                            ordenNumero = o.numero, proveedorId = o.proveedor.id, moneda = o.moneda.codigo,
                            lineas = o.lineas.filter { l -> l.cantidadPendiente.esPositiva() }.map { l ->
                                LineaRecepcionForm(
                                    producto = l.producto, cantidad = l.cantidadPendiente.texto(),
                                    costo = l.costoUnitarioEstimado?.monto?.stripTrailingZeros()?.toPlainString().orEmpty(), pendiente = l.cantidadPendiente.texto(),
                                )
                            },
                        )
                    }
                    is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
                }
            }
        }
    }

    fun elegirProveedor(id: String) = _estado.update { it.copy(proveedorId = id, erroresCampo = it.erroresCampo - "proveedor") }
    fun cambiarMoneda(v: String) = _estado.update { it.copy(moneda = v.uppercase().take(3)) }
    fun cambiarTasa(v: String) = _estado.update { it.copy(tasaCambio = v, erroresCampo = it.erroresCampo - "tasaCambio") }
    fun cambiarNotas(v: String) = _estado.update { it.copy(notas = v) }

    fun agregarLinea(producto: ProductoBreve) = _estado.update {
        if (it.lineas.any { l -> l.producto.id == producto.id }) it else it.copy(lineas = it.lineas + LineaRecepcionForm(producto, "1", ""), erroresCampo = it.erroresCampo - "lineas")
    }

    fun quitarLinea(indice: Int) = _estado.update { it.copy(lineas = it.lineas.filterIndexed { i, _ -> i != indice }) }
    fun cambiarCantidad(indice: Int, v: String) = actualizarLinea(indice) { copy(cantidad = v) }
    fun cambiarCosto(indice: Int, v: String) = actualizarLinea(indice) { copy(costo = v) }

    private fun actualizarLinea(indice: Int, cambio: LineaRecepcionForm.() -> LineaRecepcionForm) = _estado.update { s ->
        s.copy(lineas = s.lineas.mapIndexed { i, l -> if (i == indice) l.cambio() else l }, erroresCampo = s.erroresCampo.filterKeys { !it.endsWith("_$indice") })
    }

    fun confirmar() = enviar(confirmarExceso = false)

    /** RF-COM-009: solo tras ver el aviso; el borrador ya existe, solo se vuelve a confirmar. */
    fun confirmarExceso() = enviar(confirmarExceso = true)

    fun descartarExceso() = _estado.update { it.copy(avisoExceso = null) }

    private fun enviar(confirmarExceso: Boolean) {
        val s = _estado.value
        if (s.cargando) return
        val errores = validar(s)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null, avisoExceso = null) }
        viewModelScope.launch {
            val borradorId = s.borradorId ?: when (val r = compras.crearRecepcion(s.aNueva())) {
                is Resultado.Exito -> r.valor.id.also { id -> _estado.update { it.copy(borradorId = id) } }
                is Resultado.Fallo -> {
                    _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
                    return@launch
                }
            }
            when (val c = compras.confirmarRecepcion(borradorId, confirmarExceso)) {
                is ResultadoConfirmacion.Confirmada -> {
                    _estado.update { it.copy(cargando = false, pendiente = null) }
                    _sucesos.send(RecepcionSuceso.Confirmada(c.recepcion))
                }
                is ResultadoConfirmacion.ExcesoSobreOrden -> _estado.update {
                    it.copy(cargando = false, avisoExceso = AvisoExceso(c.ordenNumero, c.lineasConExceso))
                }
                is ResultadoConfirmacion.Pendiente -> _estado.update { it.copy(cargando = false, pendiente = c.clave, error = c.error.mensaje) }
                is ResultadoConfirmacion.Rechazada -> _estado.update { it.copy(cargando = false, error = c.error.mensaje) }
            }
        }
    }

    private fun RecepcionUiState.aNueva() = RecepcionNueva(
        proveedorId = proveedorId!!, ordenId = ordenId, fecha = null, moneda = moneda, tasaCambio = tasaCambio.ifBlank { null }, notas = notas.ifBlank { null },
        lineas = lineas.map { LineaRecepcionNueva(it.producto.id, it.cantidad.trim(), it.costo.trim()) },
    )

    private fun validar(s: RecepcionUiState): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (s.proveedorId == null) errores["proveedor"] = "Elige el proveedor."
        if (s.lineas.isEmpty()) errores["lineas"] = "Agrega al menos un producto."
        s.lineas.forEachIndexed { i, l ->
            val cantidad = l.cantidad.trim().toBigDecimalOrNull()
            if (cantidad == null || cantidad.signum() <= 0) errores["cantidad_$i"] = "Cantidad mayor que cero."
            val costo = l.costo.trim().toBigDecimalOrNull()
            if (costo == null || costo.signum() < 0) errores["costo_$i"] = "Escribe el costo unitario."
        }
        // RN-10: en otra moneda la tasa es obligatoria para congelar el equivalente en base.
        if (s.moneda != monedaBaseInicial && s.tasaCambio.isBlank()) errores["tasaCambio"] = "Indica la tasa de cambio."
        return errores
    }

    private val monedaBaseInicial = monedaBase

    private fun co.inventario.domain.modelo.Cantidad.texto(): String = valor.stripTrailingZeros().toPlainString()
}
