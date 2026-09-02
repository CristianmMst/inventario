package co.inventario.domain.modelo

import java.time.Instant

/** RF-AUT-005: credencial de servicio. El secreto solo existe en `ApiKeyCreada`, una vez. */
data class ApiKey(val id: String, val nombre: String, val prefijo: String, val creadaEn: Instant, val ultimoUsoEn: Instant?, val revocadaEn: Instant?)

data class ApiKeyCreada(val clave: ApiKey, val secreto: String)

/** RF-INT-005: suscripción de webhook; en v1 se persiste el contrato, no hay entrega. */
data class Suscripcion(val id: String, val url: String, val tipos: List<String>, val activa: Boolean, val descripcion: String?, val creadaEn: Instant)
