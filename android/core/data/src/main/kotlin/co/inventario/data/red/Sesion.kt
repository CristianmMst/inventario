package co.inventario.data.red

/** Tokens de la sesión actual (RF-AUT-002). */
data class Tokens(val acceso: String, val renovacion: String)

/** Lo mínimo del negocio que la app necesita sin red: su moneda base (E-01) y su nombre. */
data class DatosNegocio(val nombre: String, val monedaBase: String)

/**
 * Dónde viven los tokens y los datos del negocio. La implementación Android los persiste; los
 * tests usan una en memoria. `borrar` limpia todo: cerrar sesión no deja rastro (RF-AUT-003).
 */
interface AlmacenSesion {
    fun tokens(): Tokens?
    fun guardar(tokens: Tokens)
    fun negocio(): DatosNegocio?
    fun guardarNegocio(negocio: DatosNegocio)
    fun borrar()
}

/** Se invoca cuando la renovación ya no es posible: la app vuelve al inicio de sesión. */
fun interface AvisoSesionCerrada {
    fun sesionCerrada()
}

class AlmacenSesionEnMemoria(inicial: Tokens? = null, negocio: DatosNegocio? = null) : AlmacenSesion {
    private var actual: Tokens? = inicial
    private var datos: DatosNegocio? = negocio
    override fun tokens(): Tokens? = actual
    override fun guardar(tokens: Tokens) { actual = tokens }
    override fun negocio(): DatosNegocio? = datos
    override fun guardarNegocio(negocio: DatosNegocio) { datos = negocio }
    override fun borrar() { actual = null; datos = null }
}
