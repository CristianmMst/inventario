package co.inventario.feature.asistente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioAsistente
import co.inventario.domain.modelo.AutorMensaje
import co.inventario.domain.modelo.MensajeChat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AsistenteUiState(
    val mensajes: List<MensajeChat> = emptyList(),
    val borrador: String = "",
    val enviando: Boolean = false,
    val error: String? = null,
    /** Lo que no se pudo enviar, para reintentarlo sin volver a escribirlo. */
    val pendiente: String? = null,
) {
    val puedeEnviar: Boolean get() = borrador.isNotBlank() && !enviando
}

/**
 * Conversación con el asistente de inventario (RF-AST-001/002). El chat se abre con el primer
 * mensaje, no al entrar: mirar la pantalla no cuesta una conversación en Retell. Al salir se
 * cierra.
 */
@HiltViewModel
class AsistenteViewModel @Inject constructor(private val asistente: RepositorioAsistente) : ViewModel() {

    private val _estado = MutableStateFlow(AsistenteUiState())
    val estado: StateFlow<AsistenteUiState> = _estado.asStateFlow()

    private var chatId: String? = null

    fun cambiarBorrador(texto: String) = _estado.update { it.copy(borrador = texto) }

    fun enviar(texto: String = _estado.value.borrador) {
        val limpio = texto.trim()
        if (limpio.isEmpty() || _estado.value.enviando) return
        _estado.update {
            it.copy(
                mensajes = it.mensajes + MensajeChat(AutorMensaje.USUARIO, limpio),
                borrador = "",
                enviando = true,
                error = null,
                pendiente = null,
            )
        }
        viewModelScope.launch { conversar(limpio) }
    }

    /** Reenvía el último mensaje que falló; no se duplica en la conversación. */
    fun reintentar() {
        val texto = _estado.value.pendiente ?: return
        _estado.update { it.copy(enviando = true, error = null, pendiente = null) }
        viewModelScope.launch { conversar(texto) }
    }

    private suspend fun conversar(texto: String) {
        val id = chatId ?: when (val creado = asistente.crearChat()) {
            is Resultado.Exito -> creado.valor.also { chatId = it }
            is Resultado.Fallo -> return fallar(texto, creado.error)
        }
        when (val respuesta = asistente.enviar(id, texto)) {
            is Resultado.Exito -> _estado.update {
                it.copy(
                    enviando = false,
                    mensajes = it.mensajes + respuesta.valor.map { m -> MensajeChat(AutorMensaje.ASISTENTE, m) },
                )
            }
            is Resultado.Fallo -> {
                // Un chat cerrado o desconocido no se recupera: el próximo intento abre otro.
                if (respuesta.error.codigo in setOf("CHAT_NO_ENCONTRADO", "CHAT_TERMINADO")) chatId = null
                fallar(texto, respuesta.error)
            }
        }
    }

    private fun fallar(texto: String, error: co.inventario.common.ErrorApp) = _estado.update {
        it.copy(enviando = false, error = MapeadorErrores.paraLectura(error).mensaje, pendiente = texto)
    }

    override fun onCleared() {
        val id = chatId ?: return
        // El ViewModel ya no tiene ámbito propio; cerrar el chat es cortesía y no debe cancelarse.
        CoroutineScope(Dispatchers.IO + NonCancellable).launch { asistente.terminar(id) }
    }

    companion object {
        val SUGERENCIAS = listOf(
            "¿Qué está por agotarse?",
            "¿Qué productos están agotados?",
            "¿Qué debo comprar esta semana?",
        )
    }
}
