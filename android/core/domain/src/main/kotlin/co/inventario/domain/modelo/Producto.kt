package co.inventario.domain.modelo

enum class EstadoProducto(val codigo: String) {
    ACTIVO("activo"),
    ARCHIVADO("archivado"),
    ;

    companion object {
        fun desde(codigo: String): EstadoProducto = entries.first { it.codigo == codigo }
    }
}

data class Categoria(val id: String, val nombre: String)

/** RF-CAT-001 / RF-CAT-013: el producto expone su stock actual y sus costo y precio vigentes. */
data class Producto(
    val id: String,
    val sku: String,
    val nombre: String,
    val categoria: Categoria?,
    val unidad: UnidadMedida,
    val costoActual: Dinero?,
    val precioVenta: Dinero?,
    val stockMinimo: Cantidad?,
    val stockActual: Cantidad,
    val estado: EstadoProducto,
    val codigosBarras: List<String>,
    val imagenUrl: String?,
) {
    val estaArchivado: Boolean get() = estado == EstadoProducto.ARCHIVADO
    val bajoMinimo: Boolean get() = stockMinimo != null && stockActual <= stockMinimo
}
