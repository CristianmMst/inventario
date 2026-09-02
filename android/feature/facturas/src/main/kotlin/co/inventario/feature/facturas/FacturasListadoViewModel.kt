package co.inventario.feature.facturas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.ArchivoExportado
import co.inventario.data.repositorio.FiltrosFacturas
import co.inventario.data.repositorio.RepositorioFacturas
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.Factura
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class FacturasListadoUiState(
    val facturas: List<Factura> = emptyList(),
    val filtros: FiltrosFacturas = FiltrosFacturas(),
    val totalFiltro: Dinero? = null,
    val cantidadFiltro: Int = 0,
    val cursorSiguiente: String? = null,
    val tieneMas: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    /** Factura cuyo pago se está confirmando (diálogo con fecha, RF-FAC-004). */
    val pagando: String? = null,
    val fechaPago: String = "",
    /** Factura a la que se le vinculan recepciones (RF-FAC-006). */
    val vinculando: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    /** ZIP descargado, a la espera del menú de compartir (RF-FAC-007). */
    val archivoListo: ArchivoExportado? = null,
    val exportando: Boolean = false,
)

/** RF-FAC-004 / RF-FAC-006 / RF-FAC-007 / RF-FAC-008: listado con total, pago, vinculación y exportación. */
@HiltViewModel
class FacturasListadoViewModel @Inject constructor(private val facturas: RepositorioFacturas) : ViewModel() {

    private val _estado = MutableStateFlow(FacturasListadoUiState())
    val estado: StateFlow<FacturasListadoUiState> = _estado.asStateFlow()

    init { recargar() }

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { pedir(cursor = null) }
    }

    fun cargarMas() {
        val s = _estado.value
        if (!s.tieneMas || s.cargando || s.cursorSiguiente == null) return
        viewModelScope.launch { pedir(s.cursorSiguiente) }
    }

    fun filtrarPorEstado(estado: EstadoPago?) {
        _estado.update { it.copy(filtros = it.filtros.copy(estadoPago = estado)) }
        recargar()
    }

    fun filtrarPorProveedor(proveedorId: String?) {
        _estado.update { it.copy(filtros = it.filtros.copy(proveedorId = proveedorId)) }
        recargar()
    }

    fun filtrarPorFechas(desde: LocalDate?, hasta: LocalDate?) {
        _estado.update { it.copy(filtros = it.filtros.copy(desde = desde, hasta = hasta)) }
        recargar()
    }

    fun pedirPago(facturaId: String) = _estado.update { it.copy(pagando = facturaId, fechaPago = "", erroresCampo = emptyMap()) }
    fun cancelarPago() = _estado.update { it.copy(pagando = null, erroresCampo = emptyMap()) }
    fun cambiarFechaPago(v: String) = _estado.update { it.copy(fechaPago = v, erroresCampo = it.erroresCampo - "fechaPago") }

    fun confirmarPago() {
        val s = _estado.value
        val id = s.pagando ?: return
        val fecha = try {
            LocalDate.parse(s.fechaPago.trim())
        } catch (_: DateTimeParseException) {
            null
        }
        if (s.fechaPago.isBlank() || fecha == null) {
            _estado.update { it.copy(erroresCampo = mapOf("fechaPago" to "Marcar como pagada exige la fecha de pago (AAAA-MM-DD).")) }
            return
        }
        viewModelScope.launch {
            when (val r = facturas.pagar(id, fecha)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(pagando = null) }
                    recargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun anular(facturaId: String, motivo: String) {
        viewModelScope.launch {
            when (val r = facturas.anular(facturaId, motivo)) {
                is Resultado.Exito -> recargar()
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun pedirVinculacion(facturaId: String) = _estado.update { it.copy(vinculando = facturaId) }
    fun cancelarVinculacion() = _estado.update { it.copy(vinculando = null) }

    fun vincular(facturaId: String, recepciones: List<String>) {
        viewModelScope.launch {
            when (val r = facturas.vincular(facturaId, recepciones)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(vinculando = null) }
                    recargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    /** RF-FAC-007: descarga el ZIP; la pantalla lo entrega al menú de compartir. Sin éxito falso (RNF-07). */
    fun exportar(desde: LocalDate, hasta: LocalDate) {
        _estado.update { it.copy(exportando = true, error = null, archivoListo = null) }
        viewModelScope.launch {
            when (val r = facturas.exportar(desde, hasta)) {
                is Resultado.Exito -> _estado.update { it.copy(exportando = false, archivoListo = r.valor) }
                is Resultado.Fallo -> _estado.update { it.copy(exportando = false, error = r.error.mensaje.replace("No se guardó. ", "")) }
            }
        }
    }

    fun archivoEntregado() = _estado.update { it.copy(archivoListo = null) }

    private suspend fun pedir(cursor: String?) {
        when (val r = facturas.listar(_estado.value.filtros, cursor)) {
            is Resultado.Exito -> _estado.update {
                it.copy(
                    cargando = false,
                    facturas = if (cursor == null) r.valor.pagina.datos else it.facturas + r.valor.pagina.datos,
                    cursorSiguiente = r.valor.pagina.cursorSiguiente,
                    tieneMas = r.valor.pagina.tieneMas,
                    totalFiltro = r.valor.totalFiltro,
                    cantidadFiltro = r.valor.cantidadFiltro,
                )
            }
            is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
        }
    }
}
