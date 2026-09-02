package co.inventario.feature.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.di.AmbitoAplicacion
import co.inventario.data.repositorio.ProductoEdicion
import co.inventario.data.repositorio.ProductoNuevo
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.UnidadMedida
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

data class AltaUiState(
    val edicion: Boolean = false,
    val nombre: String = "",
    val sku: String = "",
    val unidadCodigo: String = "unidad",
    val categoriaId: String? = null,
    val costo: String = "",
    val precio: String = "",
    val stockMinimo: String = "",
    val codigoBarras: String = "",
    val fotoJpeg: ByteArray? = null,
    val imagenUrl: String? = null,
    val unidades: List<UnidadMedida> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
)

sealed interface AltaSuceso {
    data class ProductoGuardado(val productoId: String) : AltaSuceso
}

/**
 * RF-CAT-001 / RF-CAT-006 / RNF-05. Alta (con el código escaneado precargado, RF-CAT-009) o
 * edición. La foto se sube en el ámbito de aplicación **después** de crear: el producto existe y
 * la usuaria sigue aunque la subida tarde.
 */
@HiltViewModel(assistedFactory = AltaProductoViewModel.Fabrica::class)
class AltaProductoViewModel @AssistedInject constructor(
    private val catalogo: RepositorioCatalogo,
    @AmbitoAplicacion private val ambitoSubida: CoroutineScope,
    @Assisted("codigoBarras") private val codigoBarras: String?,
    @Assisted("productoId") private val productoId: String?,
    @Assisted("monedaBase") private val monedaBase: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(
            @Assisted("codigoBarras") codigoBarras: String?,
            @Assisted("productoId") productoId: String?,
            @Assisted("monedaBase") monedaBase: String,
        ): AltaProductoViewModel
    }

    private val _estado = MutableStateFlow(AltaUiState(edicion = productoId != null, codigoBarras = codigoBarras.orEmpty()))
    val estado: StateFlow<AltaUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<AltaSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    init {
        viewModelScope.launch {
            (catalogo.unidades() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(unidades = r.valor) } }
            (catalogo.categorias() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(categorias = r.valor) } }
            if (productoId != null) cargar(productoId)
        }
    }

    private suspend fun cargar(id: String) {
        _estado.update { it.copy(cargando = true) }
        when (val r = catalogo.producto(id)) {
            is Resultado.Exito -> _estado.update {
                val p = r.valor
                it.copy(
                    cargando = false, nombre = p.nombre, sku = p.sku, unidadCodigo = p.unidad.codigo, categoriaId = p.categoria?.id,
                    costo = p.costoActual?.monto?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    precio = p.precioVenta?.monto?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    stockMinimo = p.stockMinimo?.valor?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    codigoBarras = p.codigosBarras.firstOrNull().orEmpty(), imagenUrl = p.imagenUrl,
                )
            }
            is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
        }
    }

    fun cambiarNombre(v: String) = _estado.update { it.copy(nombre = v, erroresCampo = it.erroresCampo - "nombre") }
    fun cambiarSku(v: String) = _estado.update { it.copy(sku = v) }
    fun cambiarUnidad(v: String) = _estado.update { it.copy(unidadCodigo = v, erroresCampo = it.erroresCampo - "unidad") }
    fun cambiarCategoria(v: String?) = _estado.update { it.copy(categoriaId = v) }
    fun cambiarCosto(v: String) = _estado.update { it.copy(costo = v, erroresCampo = it.erroresCampo - "costo") }
    fun cambiarPrecio(v: String) = _estado.update { it.copy(precio = v, erroresCampo = it.erroresCampo - "precio") }
    fun cambiarStockMinimo(v: String) = _estado.update { it.copy(stockMinimo = v, erroresCampo = it.erroresCampo - "stockMinimo") }
    fun cambiarCodigoBarras(v: String) = _estado.update { it.copy(codigoBarras = v.trim()) }
    fun adjuntarFoto(jpeg: ByteArray) = _estado.update { it.copy(fotoJpeg = jpeg) }

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
            val resultado = if (productoId == null) {
                catalogo.crear(
                    ProductoNuevo(
                        nombre = s.nombre, unidadCodigo = s.unidadCodigo, sku = s.sku.ifBlank { null }, categoriaId = s.categoriaId,
                        costoActual = s.costo.ifBlank { null }, precioVenta = s.precio.ifBlank { null }, stockMinimo = s.stockMinimo.ifBlank { null },
                        codigosBarras = listOfNotNull(s.codigoBarras.ifBlank { null }), moneda = monedaBase,
                    ),
                )
            } else {
                catalogo.editar(
                    productoId,
                    ProductoEdicion(
                        nombre = s.nombre, unidadCodigo = s.unidadCodigo, sku = s.sku.ifBlank { null }, categoriaId = s.categoriaId,
                        costoActual = s.costo.ifBlank { null }, precioVenta = s.precio.ifBlank { null }, stockMinimo = s.stockMinimo.ifBlank { null },
                        moneda = monedaBase,
                    ),
                )
            }
            when (resultado) {
                is Resultado.Exito -> {
                    val id = resultado.valor.id
                    _estado.update { it.copy(cargando = false) }
                    _sucesos.send(AltaSuceso.ProductoGuardado(id))
                    // RNF-05: la foto ya está comprimida; se sube sin retener la pantalla.
                    s.fotoJpeg?.let { foto -> ambitoSubida.launch { catalogo.subirImagen(id, foto) } }
                }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = resultado.error.mensaje) }
            }
        }
    }

    private fun validar(s: AltaUiState): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (s.nombre.isBlank()) errores["nombre"] = "El nombre es obligatorio."
        if (s.unidadCodigo.isBlank()) errores["unidad"] = "Elige la unidad de medida."
        if (s.costo.isNotBlank() && s.costo.toBigDecimalOrNull() == null) errores["costo"] = "El costo debe ser un número."
        if (s.precio.isNotBlank() && s.precio.toBigDecimalOrNull() == null) errores["precio"] = "El precio debe ser un número."
        if (s.stockMinimo.isNotBlank() && s.stockMinimo.toBigDecimalOrNull() == null) errores["stockMinimo"] = "El mínimo debe ser un número."
        return errores
    }
}
