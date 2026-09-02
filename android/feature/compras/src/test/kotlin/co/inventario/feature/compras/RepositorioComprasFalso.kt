package co.inventario.feature.compras

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.repositorio.FiltrosOrdenes
import co.inventario.data.repositorio.FiltrosRecepciones
import co.inventario.data.repositorio.LineaOrdenNueva
import co.inventario.data.repositorio.OrdenNueva
import co.inventario.data.repositorio.ProveedorDatos
import co.inventario.data.repositorio.RecepcionNueva
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.data.repositorio.ResultadoConfirmacion
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.EstadoOrden
import co.inventario.domain.modelo.EstadoRecepcion
import co.inventario.domain.modelo.LineaOrden
import co.inventario.domain.modelo.LineaRecepcion
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Orden
import co.inventario.domain.modelo.OrdenBreve
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.ProveedorBreve
import co.inventario.domain.modelo.Recepcion
import java.time.Instant
import java.time.LocalDate

val COP = Moneda("COP")
val PRODUCTO = ProductoBreve("p1", "Cuaderno", "C-1", "unidad")

fun proveedorDePrueba(id: String = "pr1", nombre: String = "Distribuidora Norte", archivado: Boolean = false) =
    Proveedor(id, nombre, "900123", null, null, null, null, null, archivado)

fun ordenDePrueba(estado: EstadoOrden = EstadoOrden.BORRADOR, pendiente: String = "10") = Orden(
    id = "o1", numero = "OC-000001", proveedor = ProveedorBreve("pr1", "Distribuidora Norte"), estado = estado, fechaEsperada = null,
    moneda = COP, notas = null, motivoCierre = null,
    lineas = listOf(LineaOrden("ol1", PRODUCTO, Cantidad.desde("10"), Dinero.desde("2500", COP), Cantidad.desde("10") - Cantidad.desde(pendiente), Cantidad.desde(pendiente))),
    totalEstimado = Dinero.desde("25000", COP), creadaEn = Instant.parse("2026-09-02T10:00:00Z"),
)

fun recepcionDePrueba(estado: EstadoRecepcion = EstadoRecepcion.BORRADOR, conOrden: Boolean = true, exceso: Boolean = false) = Recepcion(
    id = "r1", numero = "RC-000001", proveedor = ProveedorBreve("pr1", "Distribuidora Norte"), orden = if (conOrden) OrdenBreve("o1", "OC-000001") else null,
    estado = estado, fecha = LocalDate.of(2026, 9, 2), moneda = COP, tasaCambio = "1.00000000", notas = null,
    lineas = listOf(LineaRecepcion("l1", PRODUCTO, if (conOrden) "ol1" else null, Cantidad.desde("12"), Dinero.desde("2500", COP), "1", Dinero.desde("2500", COP), exceso)),
    total = Dinero.desde("30000", COP), totalBase = Dinero.desde("30000", COP), creadaEn = Instant.parse("2026-09-02T10:00:00Z"),
)

class RepositorioComprasFalso : RepositorioCompras {
    var proveedores = mutableListOf(proveedorDePrueba())
    var conDocumentos = setOf("pr1")
    val eliminados = mutableListOf<String>()
    val archivados = mutableListOf<Pair<String, Boolean>>()
    val creados = mutableListOf<ProveedorDatos>()
    var orden = ordenDePrueba()
    val emitidas = mutableListOf<String>()
    val canceladas = mutableListOf<Pair<String, String>>()
    val cerradas = mutableListOf<Pair<String, String>>()
    val ordenesCreadas = mutableListOf<OrdenNueva>()
    val recepcionesCreadas = mutableListOf<RecepcionNueva>()
    val confirmaciones = mutableListOf<Pair<String, Boolean>>()
    var excesoHastaConfirmar = false

    override suspend fun proveedores(incluirArchivados: Boolean) = Resultado.Exito(proveedores.filter { incluirArchivados || !it.archivado })
    override suspend fun proveedor(id: String) = Resultado.Exito(proveedores.first { it.id == id })
    override suspend fun crearProveedor(datos: ProveedorDatos): Resultado<Proveedor> {
        creados += datos
        val nuevo = proveedorDePrueba(id = "pr-${creados.size + 1}", nombre = datos.nombre.orEmpty())
        proveedores += nuevo
        return Resultado.Exito(nuevo)
    }
    override suspend fun editarProveedor(id: String, datos: ProveedorDatos) = Resultado.Exito(proveedores.first { it.id == id }.copy(nombre = datos.nombre ?: ""))
    override suspend fun eliminarProveedor(id: String): Resultado<Unit> {
        if (id in conDocumentos) return Resultado.Fallo(ErrorApp("PROVEEDOR_CON_DOCUMENTOS", "Este proveedor tiene documentos. Archívalo en vez de borrarlo."))
        eliminados += id; proveedores.removeAll { it.id == id }
        return Resultado.Exito(Unit)
    }
    override suspend fun archivarProveedor(id: String, archivar: Boolean): Resultado<Proveedor> {
        archivados += id to archivar
        val p = proveedores.first { it.id == id }.copy(archivado = archivar)
        proveedores.replaceAll { if (it.id == id) p else it }
        return Resultado.Exito(p)
    }

    override suspend fun ordenes(filtros: FiltrosOrdenes, cursor: String?) = Resultado.Exito(Pagina(listOf(orden), null, false))
    override suspend fun orden(id: String) = Resultado.Exito(orden)
    override suspend fun crearOrden(datos: OrdenNueva): Resultado<Orden> { ordenesCreadas += datos; return Resultado.Exito(orden) }
    override suspend fun editarLineasOrden(id: String, lineas: List<LineaOrdenNueva>, moneda: String, notas: String?, fechaEsperada: LocalDate?) = Resultado.Exito(orden)
    override suspend fun emitirOrden(id: String): Resultado<Orden> { emitidas += id; orden = orden.copy(estado = EstadoOrden.EMITIDA); return Resultado.Exito(orden) }
    override suspend fun cancelarOrden(id: String, motivo: String): Resultado<Orden> { canceladas += id to motivo; orden = orden.copy(estado = EstadoOrden.CANCELADA, motivoCierre = motivo); return Resultado.Exito(orden) }
    override suspend fun cerrarOrdenConFaltante(id: String, motivo: String): Resultado<Orden> { cerradas += id to motivo; orden = orden.copy(estado = EstadoOrden.CERRADA_CON_FALTANTE); return Resultado.Exito(orden) }

    override suspend fun recepciones(filtros: FiltrosRecepciones, cursor: String?) = Resultado.Exito(Pagina(listOf(recepcionDePrueba()), null, false))
    override suspend fun recepcion(id: String) = Resultado.Exito(recepcionDePrueba())
    override suspend fun crearRecepcion(datos: RecepcionNueva): Resultado<Recepcion> {
        recepcionesCreadas += datos
        return Resultado.Exito(recepcionDePrueba(conOrden = datos.ordenId != null))
    }
    override suspend fun confirmarRecepcion(id: String, confirmarExceso: Boolean): ResultadoConfirmacion {
        confirmaciones += id to confirmarExceso
        if (excesoHastaConfirmar && !confirmarExceso) return ResultadoConfirmacion.ExcesoSobreOrden("OC-000001", 1)
        return ResultadoConfirmacion.Confirmada(recepcionDePrueba(EstadoRecepcion.CONFIRMADA, exceso = confirmarExceso))
    }
}
