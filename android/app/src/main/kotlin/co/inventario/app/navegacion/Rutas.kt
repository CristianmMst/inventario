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
    @Serializable data class ResolverCodigo(val codigo: String) : Ruta
    @Serializable data class Producto(val productoId: String) : Ruta
    @Serializable data class AltaProducto(val codigoBarras: String? = null) : Ruta
    @Serializable data class EditarProducto(val productoId: String) : Ruta
    @Serializable data class Movimiento(val productoId: String, val tipo: String) : Ruta
    @Serializable data class Conteo(val productoId: String) : Ruta
    @Serializable data class Historial(val productoId: String) : Ruta
    @Serializable data object Busqueda : Ruta

    // H9: compras, facturas, reportes y ajustes
    @Serializable data object Menu : Ruta
    @Serializable data object Proveedores : Ruta
    @Serializable data object Ordenes : Ruta
    @Serializable data class Orden(val ordenId: String) : Ruta
    @Serializable data object NuevaOrden : Ruta
    @Serializable data class Recepcion(val ordenId: String? = null) : Ruta
    @Serializable data object Facturas : Ruta
    @Serializable data object NuevaFactura : Ruta
    @Serializable data object Reportes : Ruta
    @Serializable data object Ajustes : Ruta
}
