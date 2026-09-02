package co.inventario.data.mapeo

import co.inventario.data.red.dto.CategoriaDto
import co.inventario.data.red.dto.DineroDto
import co.inventario.data.red.dto.MotivoDto
import co.inventario.data.red.dto.MovimientoDto
import co.inventario.data.red.dto.ProductoDto
import co.inventario.data.red.dto.UnidadMedidaDto
import co.inventario.domain.modelo.Autor
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.EstadoProducto
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Motivo
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.Origen
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.modelo.TipoUnidad
import co.inventario.domain.modelo.UnidadMedida
import java.time.Instant
import java.time.OffsetDateTime

/** Mapeadores DTO → dominio y dominio → DTO. Aquí termina el snake_case y empieza el dominio. */

fun DineroDto.aDominio(): Dinero = Dinero.desde(monto, Moneda(moneda))

fun Dinero.aDto(): DineroDto = aApi().let { DineroDto(it.monto, it.moneda) }

fun UnidadMedidaDto.aDominio(): UnidadMedida = UnidadMedida(codigo, nombre, TipoUnidad.desde(tipo), decimales)

fun CategoriaDto.aDominio(): Categoria = Categoria(id, nombre)

fun ProductoDto.aDominio(): Producto =
    Producto(
        id = id,
        sku = sku,
        nombre = nombre,
        categoria = categoria?.aDominio(),
        unidad = unidad.aDominio(),
        costoActual = costoActual?.aDominio(),
        precioVenta = precioVenta?.aDominio(),
        stockMinimo = stockMinimo?.let(Cantidad::desde),
        stockActual = Cantidad.desde(stockActual),
        estado = EstadoProducto.desde(estado),
        codigosBarras = codigosBarras,
        imagenUrl = imagen?.url,
    )

/** El servidor emite ISO 8601 con desfase (+00:00); `java.time` lo lee en API 24 con desugaring. */
fun instanteDesde(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

fun MovimientoDto.aDominio(): Movimiento =
    Movimiento(
        id = id,
        productoId = productoId,
        tipo = TipoMovimiento.desde(tipo),
        cantidad = Cantidad.desde(cantidad),
        direccion = direccion,
        motivo = motivo,
        nota = nota,
        forzado = forzado,
        stockResultante = Cantidad.desde(stockResultante),
        origen = Origen.desde(origen),
        autor = Autor(autor.tipo, autor.id),
        ocurridoEn = instanteDesde(ocurridoEn),
        anuladoEn = anuladoEn?.let(::instanteDesde),
        anulaMovimientoId = anulaMovimientoId,
        recepcionId = recepcionId,
    )

fun MotivoDto.aDominio(): Motivo = Motivo(codigo, TipoMovimiento.desde(tipoMovimiento), etiqueta, exigeNota)
