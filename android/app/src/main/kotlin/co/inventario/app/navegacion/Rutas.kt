package co.inventario.app.navegacion

import kotlinx.serialization.Serializable

/**
 * Rutas tipadas de Navigation Compose (plan.md §8.2): objetos serializables, no cadenas. Los
 * argumentos se verifican en compilación. Cada `feature` recibe lambdas de navegación; no
 * conoce estas rutas.
 */
sealed interface Ruta {
    @Serializable data object Bienvenida : Ruta
    @Serializable data object Login : Ruta
    @Serializable data object Registro : Ruta
    @Serializable data object Inicio : Ruta
    @Serializable data object Escaneo : Ruta
    @Serializable data class Producto(val productoId: String) : Ruta
    @Serializable data class AltaProducto(val codigoBarras: String? = null) : Ruta
    @Serializable data class Movimiento(val productoId: String, val tipo: String) : Ruta
    @Serializable data class Historial(val productoId: String) : Ruta
    @Serializable data object Busqueda : Ruta
}
