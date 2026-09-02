package co.inventario.feature.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioAjustes
import co.inventario.domain.modelo.ApiKey
import co.inventario.domain.modelo.Suscripcion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tipos de evento del catálogo del servidor (spec.md RF-INT-004); la lista es fija en v1. */
val TIPOS_DE_EVENTO = listOf(
    "producto.creado", "producto.actualizado", "producto.archivado",
    "movimiento.registrado", "movimiento.anulado", "inventario.discrepancia",
    "stock.bajo_minimo", "stock.agotado", "stock.repuesto",
    "proveedor.creado", "compra.ordenada", "compra.recibida", "compra.recibida_parcial", "compra.cerrada_con_faltante",
    "factura.registrada", "factura.pagada",
)

data class AjustesUiState(
    val claves: List<ApiKey> = emptyList(),
    val nombreClave: String = "",
    /** RF-AUT-005: el secreto recién creado. Se borra al confirmar que se guardó; no vuelve a verse. */
    val secretoNuevo: String? = null,
    val suscripciones: List<Suscripcion> = emptyList(),
    val urlWebhook: String = "",
    val secretoWebhook: String = "",
    val descripcionWebhook: String = "",
    val tiposElegidos: Set<String> = emptySet(),
    val tiposDisponibles: List<String> = TIPOS_DE_EVENTO,
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
)

/** RF-AUT-005 / RF-INT-005: credenciales de servicio y suscripciones de webhook desde la app. */
@HiltViewModel
class AjustesViewModel @Inject constructor(private val ajustes: RepositorioAjustes) : ViewModel() {

    private val _estado = MutableStateFlow(AjustesUiState())
    val estado: StateFlow<AjustesUiState> = _estado.asStateFlow()

    init { recargar() }

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val claves = ajustes.apiKeys()
            val suscripciones = ajustes.suscripciones()
            _estado.update {
                it.copy(
                    cargando = false,
                    claves = (claves as? Resultado.Exito)?.valor ?: it.claves,
                    suscripciones = (suscripciones as? Resultado.Exito)?.valor ?: it.suscripciones,
                    error = listOf(claves, suscripciones).filterIsInstance<Resultado.Fallo>().firstOrNull()?.error?.mensaje,
                )
            }
        }
    }

    fun cambiarNombreClave(v: String) = _estado.update { it.copy(nombreClave = v, erroresCampo = it.erroresCampo - "nombreClave") }

    fun crearClave() {
        val nombre = _estado.value.nombreClave.trim()
        if (nombre.isBlank()) {
            _estado.update { it.copy(erroresCampo = it.erroresCampo + ("nombreClave" to "Dale un nombre a la credencial (por ejemplo, «Caja»).")) }
            return
        }
        viewModelScope.launch {
            when (val r = ajustes.crearApiKey(nombre)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(nombreClave = "", secretoNuevo = r.valor.secreto) }
                    recargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    /** La usuaria ya copió el secreto: desaparece para siempre de la app (RF-AUT-005). */
    fun secretoGuardado() = _estado.update { it.copy(secretoNuevo = null) }

    fun revocarClave(id: String) {
        viewModelScope.launch {
            when (val r = ajustes.revocarApiKey(id)) {
                is Resultado.Exito -> recargar()
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun cambiarUrlWebhook(v: String) = _estado.update { it.copy(urlWebhook = v, erroresCampo = it.erroresCampo - "url") }
    fun cambiarSecretoWebhook(v: String) = _estado.update { it.copy(secretoWebhook = v, erroresCampo = it.erroresCampo - "secreto") }
    fun cambiarDescripcionWebhook(v: String) = _estado.update { it.copy(descripcionWebhook = v) }
    fun alternarTipo(tipo: String) = _estado.update {
        it.copy(tiposElegidos = if (tipo in it.tiposElegidos) it.tiposElegidos - tipo else it.tiposElegidos + tipo, erroresCampo = it.erroresCampo - "tipos")
    }

    fun crearSuscripcion() {
        val s = _estado.value
        val errores = mutableMapOf<String, String>()
        if (!s.urlWebhook.trim().startsWith("https://")) errores["url"] = "La URL debe empezar por https://."
        if (s.tiposElegidos.isEmpty()) errores["tipos"] = "Elige al menos un tipo de evento."
        if (s.secretoWebhook.length < 32) errores["secreto"] = "El secreto de firma tiene al menos 32 caracteres."
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        viewModelScope.launch {
            when (val r = ajustes.crearSuscripcion(s.urlWebhook, s.tiposElegidos.toList(), s.secretoWebhook, s.descripcionWebhook.ifBlank { null })) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(urlWebhook = "", secretoWebhook = "", descripcionWebhook = "", tiposElegidos = emptySet()) }
                    recargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun eliminarSuscripcion(id: String) {
        viewModelScope.launch {
            when (val r = ajustes.eliminarSuscripcion(id)) {
                is Resultado.Exito -> recargar()
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }
}
