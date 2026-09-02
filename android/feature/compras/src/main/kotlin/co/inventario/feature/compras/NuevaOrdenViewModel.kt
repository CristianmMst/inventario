package co.inventario.feature.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.LineaOrdenNueva
import co.inventario.data.repositorio.OrdenNueva
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.Proveedor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class LineaOrdenForm(val producto: ProductoBreve, val cantidad: String, val costo: String)

data class NuevaOrdenUiState(
    val proveedores: List<Proveedor> = emptyList(),
    val proveedorId: String? = null,
    val fechaEsperada: String = "",
    val notas: String = "",
    val moneda: String,
    val lineas: List<LineaOrdenForm> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
)

sealed interface NuevaOrdenSuceso {
    data class Creada(val ordenId: String) : NuevaOrdenSuceso
}

/** RF-COM-002: orden de compra en borrador con proveedor, fecha esperada, notas y líneas. */
@HiltViewModel(assistedFactory = NuevaOrdenViewModel.Fabrica::class)
class NuevaOrdenViewModel @AssistedInject constructor(
    private val compras: RepositorioCompras,
    @Assisted monedaBase: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(monedaBase: String): NuevaOrdenViewModel
    }

    private val _estado = MutableStateFlow(NuevaOrdenUiState(moneda = monedaBase))
    val estado: StateFlow<NuevaOrdenUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<NuevaOrdenSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    init {
        viewModelScope.launch { (compras.proveedores() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(proveedores = r.valor) } } }
    }

    fun elegirProveedor(id: String) = _estado.update { it.copy(proveedorId = id, erroresCampo = it.erroresCampo - "proveedor") }
    fun cambiarFechaEsperada(v: String) = _estado.update { it.copy(fechaEsperada = v, erroresCampo = it.erroresCampo - "fechaEsperada") }
    fun cambiarNotas(v: String) = _estado.update { it.copy(notas = v) }
    fun agregarLinea(producto: ProductoBreve) = _estado.update {
        if (it.lineas.any { l -> l.producto.id == producto.id }) it else it.copy(lineas = it.lineas + LineaOrdenForm(producto, "1", ""), erroresCampo = it.erroresCampo - "lineas")
    }
    fun quitarLinea(indice: Int) = _estado.update { it.copy(lineas = it.lineas.filterIndexed { i, _ -> i != indice }) }
    fun cambiarCantidad(indice: Int, v: String) = _estado.update { s -> s.copy(lineas = s.lineas.mapIndexed { i, l -> if (i == indice) l.copy(cantidad = v) else l }) }
    fun cambiarCosto(indice: Int, v: String) = _estado.update { s -> s.copy(lineas = s.lineas.mapIndexed { i, l -> if (i == indice) l.copy(costo = v) else l }) }

    fun guardar() {
        val s = _estado.value
        if (s.cargando) return
        val errores = mutableMapOf<String, String>()
        if (s.proveedorId == null) errores["proveedor"] = "Elige el proveedor."
        if (s.lineas.isEmpty()) errores["lineas"] = "Agrega al menos un producto."
        s.lineas.forEachIndexed { i, l -> if ((l.cantidad.trim().toBigDecimalOrNull()?.signum() ?: 0) <= 0) errores["cantidad_$i"] = "Cantidad mayor que cero." }
        val fecha = s.fechaEsperada.trim().ifBlank { null }?.let { try { LocalDate.parse(it) } catch (_: DateTimeParseException) { errores["fechaEsperada"] = "Fecha AAAA-MM-DD."; null } }
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val datos = OrdenNueva(
                proveedorId = s.proveedorId!!, fechaEsperada = fecha, moneda = s.moneda, notas = s.notas.ifBlank { null },
                lineas = s.lineas.map { LineaOrdenNueva(it.producto.id, it.cantidad.trim(), it.costo.trim().ifBlank { null }) },
            )
            when (val r = compras.crearOrden(datos)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false) }
                    _sucesos.send(NuevaOrdenSuceso.Creada(r.valor.id))
                }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }
}

/** Buscador de productos para añadir líneas (órdenes y recepciones). Antirrebote de 300 ms. */
@HiltViewModel
class BuscadorProductosViewModel @Inject constructor(private val catalogo: RepositorioCatalogo) : ViewModel() {
    data class Estado(val texto: String = "", val resultados: List<Producto> = emptyList())

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()
    private var trabajo: Job? = null

    fun cambiarTexto(v: String) {
        _estado.update { it.copy(texto = v) }
        trabajo?.cancel()
        if (v.isBlank()) {
            _estado.update { it.copy(resultados = emptyList()) }
            return
        }
        trabajo = viewModelScope.launch {
            delay(300)
            (catalogo.buscar(v.trim()) as? Resultado.Exito)?.let { r -> _estado.update { it.copy(resultados = r.valor.datos) } }
        }
    }

    fun limpiar() = _estado.update { Estado() }
}

fun Producto.breve() = ProductoBreve(id, nombre, sku, unidad.codigo)
