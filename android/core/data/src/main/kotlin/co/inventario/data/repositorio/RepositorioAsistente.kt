package co.inventario.data.repositorio

import co.inventario.common.Resultado
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.MensajeAsistenteDto
import co.inventario.data.red.llamada
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RF-AST-001/002: conversación con el asistente de inventario. La app nunca habla con Retell:
 * el servidor guarda la clave y responde con los mensajes del asistente.
 */
interface RepositorioAsistente {
    suspend fun crearChat(): Resultado<String>
    suspend fun enviar(chatId: String, texto: String): Resultado<List<String>>
    suspend fun terminar(chatId: String): Resultado<Unit>
}

@Singleton
class RepositorioAsistenteApi @Inject constructor(private val api: InventarioApi) : RepositorioAsistente {
    override suspend fun crearChat() = llamada({ api.crearChatAsistente() }) { it.chatId }
    override suspend fun enviar(chatId: String, texto: String) =
        llamada({ api.mensajeAsistente(chatId, MensajeAsistenteDto(texto)) }) { it.mensajes }
    override suspend fun terminar(chatId: String) = llamada({ api.terminarChatAsistente(chatId) }) { }
}
