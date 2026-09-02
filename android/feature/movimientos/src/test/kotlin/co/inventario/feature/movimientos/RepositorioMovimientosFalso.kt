package co.inventario.feature.movimientos

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RegistroMovimiento
import co.inventario.data.repositorio.RepositorioMovimientos
import co.inventario.data.repositorio.ResultadoConteo
import co.inventario.data.repositorio.ResultadoMovimiento
import co.inventario.domain.modelo.Autor
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.Conteo
import co.inventario.domain.modelo.EstadoProducto
import co.inventario.domain.modelo.Motivo
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.Origen
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.modelo.TipoUnidad
import co.inventario.domain.modelo.UnidadMedida
import java.time.Instant

val UNIDAD = UnidadMedida("unidad", "Unidad", TipoUnidad.DISCRETA, 0)

fun productoDePrueba(stock: String = "10") = Producto(
    id = "p1", sku = "CUAD-100", nombre = "Cuaderno 100 hojas", categoria = Categoria("c1", "Papelería"), unidad = UNIDAD,
    costoActual = null, precioVenta = null, stockMinimo = Cantidad.desde("5"), stockActual = Cantidad.desde(stock),
    estado = EstadoProducto.ACTIVO, codigosBarras = listOf("7701234567890"), imagenUrl = null,
)

fun movimientoDePrueba(
    id: String = "m1", tipo: TipoMovimiento = TipoMovimiento.SALIDA, cantidad: String = "1", stockResultante: String = "9",
    anuladoEn: Instant? = null, anulaMovimientoId: String? = null, forzado: Boolean = false,
) = Movimiento(
    id = id, productoId = "p1", tipo = tipo, cantidad = Cantidad.desde(cantidad), direccion = if (tipo.restaStock) -1 else 1,
    motivo = "venta", nota = null, forzado = forzado, stockResultante = Cantidad.desde(stockResultante), origen = Origen.APP,
    autor = Autor("usuario", "u1"), ocurridoEn = Instant.parse("2026-09-02T10:00:00Z"), anuladoEn = anuladoEn,
    anulaMovimientoId = anulaMovimientoId, recepcionId = null,
)

/** La lista cerrada de RF-INV-010, tal como la sirve el servidor. */
val MOTIVOS = listOf(
    Motivo("recepcion_compra", TipoMovimiento.ENTRADA, "Recepción de compra", false),
    Motivo("carga_inicial", TipoMovimiento.ENTRADA, "Carga inicial", false),
    Motivo("otro", TipoMovimiento.ENTRADA, "Otro", true),
    Motivo("venta", TipoMovimiento.SALIDA, "Venta", false),
    Motivo("consumo_interno", TipoMovimiento.SALIDA, "Consumo interno", false),
    Motivo("otro", TipoMovimiento.SALIDA, "Otro", true),
    Motivo("rotura", TipoMovimiento.MERMA, "Rotura", false),
    Motivo("vencimiento", TipoMovimiento.MERMA, "Vencimiento", false),
    Motivo("otro", TipoMovimiento.MERMA, "Otro", true),
    Motivo("conteo_fisico", TipoMovimiento.AJUSTE, "Conteo físico", false),
    Motivo("otro", TipoMovimiento.AJUSTE, "Otro", true),
)

class RepositorioMovimientosFalso : RepositorioMovimientos {
    var producto = productoDePrueba()
    val registros = mutableListOf<RegistroMovimiento>()
    val anulaciones = mutableListOf<Pair<String, String?>>()
    val conteos = mutableListOf<Pair<String, String>>()
    val reintentos = mutableListOf<String>()
    var respuestaRegistro: ((RegistroMovimiento) -> ResultadoMovimiento)? = null
    var historial: List<Movimiento> = listOf(movimientoDePrueba())

    override suspend fun producto(productoId: String): Resultado<Producto> = Resultado.Exito(producto)

    override suspend fun motivos(tipo: TipoMovimiento): Resultado<List<Motivo>> = Resultado.Exito(MOTIVOS.filter { it.tipo == tipo })

    override suspend fun registrar(registro: RegistroMovimiento): ResultadoMovimiento {
        registros += registro
        return respuestaRegistro?.invoke(registro)
            ?: ResultadoMovimiento.Confirmado(movimientoDePrueba(cantidad = registro.cantidad, tipo = registro.tipo, forzado = registro.forzar))
    }

    override suspend fun reintentar(clave: String): ResultadoMovimiento {
        reintentos += clave
        return ResultadoMovimiento.Confirmado(movimientoDePrueba())
    }

    override suspend fun contar(productoId: String, cantidadContada: String, nota: String?): ResultadoConteo {
        conteos += productoId to cantidadContada
        val diferencia = Cantidad.desde(cantidadContada) - producto.stockActual
        return ResultadoConteo.Confirmado(
            Conteo(producto.stockActual, Cantidad.desde(cantidadContada), diferencia, movimientoDePrueba(tipo = TipoMovimiento.AJUSTE, cantidad = diferencia.valor.abs().toPlainString())),
        )
    }

    override suspend fun anular(movimientoId: String, nota: String?): ResultadoMovimiento {
        anulaciones += movimientoId to nota
        val original = historial.first { it.id == movimientoId }.copy(anuladoEn = Instant.parse("2026-09-02T11:00:00Z"))
        val contra = movimientoDePrueba(id = "c-$movimientoId", tipo = TipoMovimiento.CONTRAMOVIMIENTO, anulaMovimientoId = movimientoId, stockResultante = "10")
        historial = listOf(contra) + historial.map { if (it.id == movimientoId) original else it }
        return ResultadoMovimiento.Confirmado(contra)
    }

    override suspend fun historial(productoId: String, cursor: String?): Resultado<Pagina<Movimiento>> = Resultado.Exito(Pagina(historial, null, false))

    fun fallaSiempre(error: ErrorApp) { respuestaRegistro = { ResultadoMovimiento.Rechazado(error) } }
}
