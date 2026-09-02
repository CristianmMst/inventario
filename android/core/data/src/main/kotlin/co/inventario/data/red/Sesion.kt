package co.inventario.data.red

/** Tokens de la sesión actual (RF-AUT-002). */
data class Tokens(val acceso: String, val renovacion: String)

/**
 * Dónde viven los tokens. La implementación Android los persiste; los tests usan una en
 * memoria. `alCerrar` avisa a la UI de que hay que volver a iniciar sesión (RF-AUT-003).
 */
interface AlmacenSesion {
    fun tokens(): Tokens?
    fun guardar(tokens: Tokens)
    fun borrar()
}

/** Se invoca cuando la renovación ya no es posible: la app vuelve al inicio de sesión. */
fun interface AvisoSesionCerrada {
    fun sesionCerrada()
}

class AlmacenSesionEnMemoria(inicial: Tokens? = null) : AlmacenSesion {
    private var actual: Tokens? = inicial
    override fun tokens(): Tokens? = actual
    override fun guardar(tokens: Tokens) { actual = tokens }
    override fun borrar() { actual = null }
}
