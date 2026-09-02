package co.inventario.data.repositorio

import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.ApiKeyNuevaDto
import co.inventario.data.red.dto.SuscripcionNuevaDto
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.ApiKey
import co.inventario.domain.modelo.ApiKeyCreada
import co.inventario.domain.modelo.Suscripcion
import javax.inject.Inject
import javax.inject.Singleton

/** RF-AUT-005 / RF-INT-005: credenciales de servicio y suscripciones de webhook. */
interface RepositorioAjustes {
    suspend fun apiKeys(): Resultado<List<ApiKey>>
    /** El secreto viene solo en esta respuesta; no se vuelve a poder consultar. */
    suspend fun crearApiKey(nombre: String): Resultado<ApiKeyCreada>
    suspend fun revocarApiKey(id: String): Resultado<Unit>
    suspend fun suscripciones(): Resultado<List<Suscripcion>>
    suspend fun crearSuscripcion(url: String, tipos: List<String>, secreto: String, descripcion: String?): Resultado<Suscripcion>
    suspend fun eliminarSuscripcion(id: String): Resultado<Unit>
}

@Singleton
class RepositorioAjustesApi @Inject constructor(private val api: InventarioApi) : RepositorioAjustes {

    override suspend fun apiKeys(): Resultado<List<ApiKey>> = llamada({ api.apiKeys() }) { p -> p.datos.map { it.aDominio() } }

    override suspend fun crearApiKey(nombre: String): Resultado<ApiKeyCreada> =
        llamada({ api.crearApiKey(ApiKeyNuevaDto(nombre.trim())) }) { ApiKeyCreada(it.aDominio(), it.clave.orEmpty()) }

    override suspend fun revocarApiKey(id: String): Resultado<Unit> = llamada({ api.revocarApiKey(id) }) { }

    override suspend fun suscripciones(): Resultado<List<Suscripcion>> = llamada({ api.suscripciones() }) { p -> p.datos.map { it.aDominio() } }

    override suspend fun crearSuscripcion(url: String, tipos: List<String>, secreto: String, descripcion: String?): Resultado<Suscripcion> =
        llamada({ api.crearSuscripcion(SuscripcionNuevaDto(url.trim(), tipos, secreto, descripcion?.trim()?.ifBlank { null })) }) { it.aDominio() }

    override suspend fun eliminarSuscripcion(id: String): Resultado<Unit> = llamada({ api.eliminarSuscripcion(id) }) { }
}
