package co.inventario.domain.modelo

/** Un mensaje del chat con el asistente de inventario (RF-AST-002). */
data class MensajeChat(val autor: AutorMensaje, val texto: String)

enum class AutorMensaje { USUARIO, ASISTENTE }
