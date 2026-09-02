package co.inventario.feature.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RegistroMovimiento
import co.inventario.data.repositorio.RepositorioMovimientos
import co.inventario.data.repositorio.ResultadoMovimiento
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Motivo
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.regla.ReglaViolada
import co.inventario.domain.regla.validarCantidadMovimiento
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

/** RF-INV-005/006: lo que el servidor dijo al rechazar; la usuaria decide si fuerza. */
data class StockInsuficienteAviso(val disponible: Cantidad, val solicitado: Cantidad, val puedeForzar: Boolean)

data class MovimientoUiState(
    val tipo: TipoMovimiento,
    val producto: Producto? = null,
    val motivos: List<Motivo> = emptyList(),
    val cantidad: String = "1",
    val motivo: String = "",
    val nota: String = "",
    val direccion: Int? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val override: StockInsuficienteAviso? = null,
    /** Clave de la operación guardada sin confirmación (RNF-06); permite reintentar a mano. */
    val pendiente: String? = null,
) {
    val motivoElegido: Motivo? get() = motivos.firstOrNull { it.codigo == motivo }
    val notaObligatoria: Boolean get() = tipo == TipoMovimiento.MERMA || tipo == TipoMovimiento.AJUSTE || motivoElegido?.exigeNota == true
}

sealed interface MovimientoSuceso {
    data class Registrado(val movimiento: Movimiento) : MovimientoSuceso
}

/**
 * Registrar entrada, salida, merma o ajuste (RF-INV-001, RF-INV-010). Sale precargado con
 * cantidad 1 y el primer motivo: desde el escaneo son tres toques (RNF-08). El 409 de stock se
 * convierte en un diálogo que dice cuánto hay y ofrece forzar con motivo escrito (RF-INV-006).
 */
@HiltViewModel(assistedFactory = MovimientoViewModel.Fabrica::class)
class MovimientoViewModel @AssistedInject constructor(
    private val repositorio: RepositorioMovimientos,
    @Assisted("productoId") private val productoId: String,
    @Assisted private val tipo: TipoMovimiento,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(@Assisted("productoId") productoId: String, tipo: TipoMovimiento): MovimientoViewModel
    }

    private val _estado = MutableStateFlow(MovimientoUiState(tipo = tipo))
    val estado: StateFlow<MovimientoUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<MovimientoSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    init {
        viewModelScope.launch {
            (repositorio.producto(productoId) as? Resultado.Exito)?.let { r -> _estado.update { it.copy(producto = r.valor) } }
            when (val r = repositorio.motivos(tipo)) {
                is Resultado.Exito -> _estado.update { s ->
                    // "recepcion_compra" lo pone el sistema al recibir; no se ofrece a mano (RF-INV-010).
                    val ofrecidos = r.valor.filterNot { it.codigo == "recepcion_compra" }
                    s.copy(motivos = ofrecidos, motivo = s.motivo.ifBlank { ofrecidos.firstOrNull()?.codigo.orEmpty() })
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun cambiarCantidad(v: String) = _estado.update { it.copy(cantidad = v, erroresCampo = it.erroresCampo - "cantidad") }
    fun cambiarMotivo(v: String) = _estado.update { it.copy(motivo = v, erroresCampo = it.erroresCampo - "motivo" - "nota") }
    fun cambiarNota(v: String) = _estado.update { it.copy(nota = v, erroresCampo = it.erroresCampo - "nota") }
    fun cambiarDireccion(v: Int) = _estado.update { it.copy(direccion = v, erroresCampo = it.erroresCampo - "direccion") }
    fun cancelarOverride() = _estado.update { it.copy(override = null) }

    fun confirmar() = enviar(forzar = false)

    /** RF-INV-006: el override es una confirmación explícita y exige nota. */
    fun forzar() = enviar(forzar = true)

    fun reintentar() {
        val clave = _estado.value.pendiente ?: return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { tratar(repositorio.reintentar(clave)) }
    }

    private fun enviar(forzar: Boolean) {
        val s = _estado.value
        if (s.cargando) return
        val errores = validar(s, forzar)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            tratar(
                repositorio.registrar(
                    RegistroMovimiento(
                        productoId = productoId, tipo = tipo, cantidad = s.cantidad.trim(), motivo = s.motivo,
                        nota = s.nota.ifBlank { null }, forzar = forzar, direccion = if (tipo == TipoMovimiento.AJUSTE) s.direccion else null,
                    ),
                ),
            )
        }
    }

    private suspend fun tratar(resultado: ResultadoMovimiento) {
        when (resultado) {
            is ResultadoMovimiento.Confirmado -> {
                _estado.update { it.copy(cargando = false, override = null, pendiente = null) }
                _sucesos.send(MovimientoSuceso.Registrado(resultado.movimiento))
            }
            is ResultadoMovimiento.StockInsuficiente -> _estado.update {
                it.copy(cargando = false, override = StockInsuficienteAviso(resultado.disponible, resultado.solicitado, resultado.puedeForzar))
            }
            is ResultadoMovimiento.Pendiente -> _estado.update {
                it.copy(cargando = false, pendiente = resultado.clave, error = resultado.error.mensaje)
            }
            is ResultadoMovimiento.Rechazado -> _estado.update { it.copy(cargando = false, error = resultado.error.mensaje) }
        }
    }

    private fun validar(s: MovimientoUiState, forzar: Boolean): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        val cantidad = s.cantidad.trim().toBigDecimalOrNull()?.let(::Cantidad)
        if (cantidad == null) {
            errores["cantidad"] = "Escribe la cantidad."
        } else {
            val unidad = s.producto?.unidad
            if (unidad != null) {
                try {
                    validarCantidadMovimiento(cantidad, unidad)
                } catch (e: ReglaViolada) {
                    errores["cantidad"] = e.message ?: "La cantidad no es válida."
                }
            } else if (!cantidad.esPositiva()) {
                errores["cantidad"] = "La cantidad debe ser mayor que cero."
            }
        }
        if (s.motivo.isBlank()) errores["motivo"] = "Elige un motivo."
        if ((s.notaObligatoria || forzar) && s.nota.isBlank()) {
            errores["nota"] = if (forzar) "Para forzar la salida escribe por qué." else "Este movimiento exige una nota."
        }
        if (tipo == TipoMovimiento.AJUSTE && s.direccion == null) errores["direccion"] = "Indica si el ajuste suma o resta."
        return errores
    }
}
