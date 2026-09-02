package co.inventario.data.repositorio

import co.inventario.common.Resultado
import co.inventario.data.red.AlmacenSesion
import co.inventario.data.red.DatosNegocio
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Tokens
import co.inventario.data.red.dto.LoginDto
import co.inventario.data.red.dto.NegocioNuevoDto
import co.inventario.data.red.dto.RegistroDto
import co.inventario.data.red.dto.SesionDto
import co.inventario.data.red.dto.TokenRenovacionDto
import co.inventario.data.red.llamada
import javax.inject.Inject
import javax.inject.Singleton

data class SesionIniciada(val nombreUsuario: String, val nombreNegocio: String, val monedaBase: String)

/** RF-AUT-001..003: registro, inicio y cierre de sesión. Guarda los tokens al entrar. */
interface RepositorioSesion {
    fun haySesion(): Boolean
    suspend fun registrar(email: String, password: String, nombre: String, negocio: String, moneda: String, zonaHoraria: String): Resultado<SesionIniciada>
    suspend fun iniciarSesion(email: String, password: String): Resultado<SesionIniciada>
    suspend fun cerrarSesion()
}

@Singleton
class RepositorioSesionApi @Inject constructor(
    private val api: InventarioApi,
    private val almacen: AlmacenSesion,
) : RepositorioSesion {

    override fun haySesion(): Boolean = almacen.tokens() != null

    override suspend fun registrar(
        email: String, password: String, nombre: String, negocio: String, moneda: String, zonaHoraria: String,
    ): Resultado<SesionIniciada> =
        llamada({ api.registro(RegistroDto(email, password, nombre, NegocioNuevoDto(negocio, moneda, zonaHoraria))) }, ::guardar)

    override suspend fun iniciarSesion(email: String, password: String): Resultado<SesionIniciada> =
        llamada({ api.login(LoginDto(email, password)) }, ::guardar)

    override suspend fun cerrarSesion() {
        val tokens = almacen.tokens()
        almacen.borrar()
        if (tokens != null) {
            runCatching { api.logout(TokenRenovacionDto(tokens.renovacion)) }
        }
    }

    private fun guardar(sesion: SesionDto): SesionIniciada {
        almacen.guardar(Tokens(sesion.tokenAcceso, sesion.tokenRenovacion))
        almacen.guardarNegocio(DatosNegocio(sesion.negocio.nombre, sesion.negocio.monedaBase))
        return SesionIniciada(sesion.usuario.nombre, sesion.negocio.nombre, sesion.negocio.monedaBase)
    }
}
