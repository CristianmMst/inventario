package co.inventario.data.red

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.red.dto.ErrorEnvoltorioDto
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Convierte una respuesta HTTP en `Resultado`, traduciendo el sobre de error de la API
 * (constitution.md §3) a texto en español por el mapeador. RNF-07: el texto crudo del
 * servidor no sale de aquí.
 */
suspend fun <T, R> llamada(bloque: suspend () -> Response<T>, transformar: (T) -> R): Resultado<R> =
    try {
        val respuesta = bloque()
        val cuerpo = respuesta.body()
        if (respuesta.isSuccessful && cuerpo != null) {
            Resultado.Exito(transformar(cuerpo))
        } else if (respuesta.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            Resultado.Exito(transformar(Unit as T))
        } else {
            Resultado.Fallo(errorDe(respuesta))
        }
    } catch (_: SocketTimeoutException) {
        Resultado.Fallo(MapeadorErrores.error(MapeadorErrores.TIEMPO_AGOTADO))
    } catch (_: IOException) {
        Resultado.Fallo(MapeadorErrores.error(MapeadorErrores.SIN_RED))
    }

fun errorDe(respuesta: Response<*>): ErrorApp {
    val requestId = respuesta.headers()["X-Request-Id"]
    val texto = respuesta.errorBody()?.string().orEmpty()
    val sobre = try {
        Json.decodeFromString(ErrorEnvoltorioDto.serializer(), texto).error
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    if (sobre == null) {
        val codigo = if (respuesta.code() >= 500) MapeadorErrores.ERROR_SERVIDOR else MapeadorErrores.DESCONOCIDO
        return MapeadorErrores.error(codigo, mapOf("request_id" to (requestId ?: "")), requestId)
    }
    val detalles = sobre.details.keys.associateWith { sobre.detalle(it).orEmpty() }
    return MapeadorErrores.error(sobre.code, detalles, requestId)
}
