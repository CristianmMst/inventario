package co.inventario.data.red

import co.inventario.data.red.dto.SesionDto
import co.inventario.data.red.dto.TokenRenovacionDto
import kotlinx.serialization.encodeToString
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

private const val CABECERA = "Authorization"

/** Añade `Authorization: Bearer` a toda petición cuando hay sesión (RF-AUT-002). */
class InterceptorAutenticacion(private val almacen: AlmacenSesion) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = almacen.tokens()?.acceso ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header(CABECERA, "Bearer $token").build())
    }
}

/**
 * Ante un 401 renueva el token con `POST /auth/refresh` y reintenta **una sola vez**
 * (RF-AUT-003). Un segundo 401 seguido, o un fallo al renovar, cierra la sesión: los tokens
 * se borran y se avisa a la app. Nunca entra en bucle.
 */
class RenovadorSesion(
    private val almacen: AlmacenSesion,
    private val baseUrl: String,
    private val aviso: AvisoSesionCerrada,
    clienteBase: OkHttpClient = OkHttpClient(),
) : Authenticator {
    // Cliente sin este Authenticator: la renovación no se autentica a sí misma.
    private val cliente = clienteBase.newBuilder().authenticator(Authenticator.NONE).build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) {
            // Ya se reintentó con un token nuevo y volvió el 401: la sesión no vale.
            cerrar()
            return null
        }
        val tokens = almacen.tokens() ?: return null
        val nuevos = synchronized(this) {
            val vigentes = almacen.tokens()
            // Otra petición pudo renovar mientras esperábamos el candado: reutilizar su token.
            if (vigentes != null && vigentes.acceso != tokens.acceso) vigentes else renovar(tokens.renovacion)
        }
        if (nuevos == null) {
            cerrar()
            return null
        }
        return response.request.newBuilder().header(CABECERA, "Bearer ${nuevos.acceso}").build()
    }

    private fun renovar(tokenRenovacion: String): Tokens? {
        val cuerpo = Json.encodeToString(TokenRenovacionDto(tokenRenovacion))
        val peticion = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/v1/auth/refresh")
            .post(cuerpo.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            cliente.newCall(peticion).execute().use { respuesta ->
                if (!respuesta.isSuccessful) return null
                val sesion = Json.decodeFromString(SesionDto.serializer(), respuesta.body.string())
                Tokens(sesion.tokenAcceso, sesion.tokenRenovacion).also(almacen::guardar)
            }
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun cerrar() {
        almacen.borrar()
        aviso.sesionCerrada()
    }
}
