package co.inventario.data.mapeo

import co.inventario.data.red.dto.ApiKeyDto
import co.inventario.data.red.dto.ComprasCategoriaDto
import co.inventario.data.red.dto.ComprasProveedorDto
import co.inventario.data.red.dto.FacturaDto
import co.inventario.data.red.dto.FilaAgotadoDto
import co.inventario.data.red.dto.FilaBajoMinimoDto
import co.inventario.data.red.dto.FilaDiscrepanciaDto
import co.inventario.data.red.dto.FilaNoValorizableDto
import co.inventario.data.red.dto.FilaSinMovimientoDto
import co.inventario.data.red.dto.ImagenDto
import co.inventario.data.red.dto.LineaOrdenDto
import co.inventario.data.red.dto.LineaRecepcionDto
import co.inventario.data.red.dto.MermaMotivoDto
import co.inventario.data.red.dto.MermaProductoDto
import co.inventario.data.red.dto.OrdenBreveDto
import co.inventario.data.red.dto.OrdenDto
import co.inventario.data.red.dto.ProductoBreveDto
import co.inventario.data.red.dto.ProveedorBreveDto
import co.inventario.data.red.dto.ProveedorDto
import co.inventario.data.red.dto.RecepcionBreveDto
import co.inventario.data.red.dto.RecepcionDto
import co.inventario.data.red.dto.ResumenComprasDto
import co.inventario.data.red.dto.ResumenMermasDto
import co.inventario.data.red.dto.SuscripcionDto
import co.inventario.data.red.dto.ValorCategoriaDto
import co.inventario.data.red.dto.ValorizacionDto
import co.inventario.domain.modelo.ApiKey
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.ComprasCategoria
import co.inventario.domain.modelo.ComprasProveedor
import co.inventario.domain.modelo.EstadoOrden
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.EstadoRecepcion
import co.inventario.domain.modelo.Factura
import co.inventario.domain.modelo.FilaAgotado
import co.inventario.domain.modelo.FilaBajoMinimo
import co.inventario.domain.modelo.FilaDiscrepancia
import co.inventario.domain.modelo.FilaNoValorizable
import co.inventario.domain.modelo.FilaSinMovimiento
import co.inventario.domain.modelo.Imagen
import co.inventario.domain.modelo.LineaOrden
import co.inventario.domain.modelo.LineaRecepcion
import co.inventario.domain.modelo.MermaMotivo
import co.inventario.domain.modelo.MermaProducto
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Orden
import co.inventario.domain.modelo.OrdenBreve
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.ProveedorBreve
import co.inventario.domain.modelo.Recepcion
import co.inventario.domain.modelo.RecepcionBreve
import co.inventario.domain.modelo.ResumenCompras
import co.inventario.domain.modelo.ResumenMermas
import co.inventario.domain.modelo.Suscripcion
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.modelo.ValorCategoria
import co.inventario.domain.modelo.Valorizacion
import java.time.LocalDate

fun ProveedorDto.aDominio() = Proveedor(id, nombre, identificacionFiscal, contacto, telefono, email, direccion, notas, archivado = estado == "archivado")

fun ProveedorBreveDto.aDominio() = ProveedorBreve(id, nombre)

fun ProductoBreveDto.aDominio() = ProductoBreve(id, nombre, sku, unidadCodigo)

fun OrdenBreveDto.aDominio() = OrdenBreve(id, numero)

fun LineaOrdenDto.aDominio() = LineaOrden(
    id, producto.aDominio(), Cantidad.desde(cantidadOrdenada), costoUnitarioEstimado?.aDominio(),
    Cantidad.desde(cantidadRecibida), Cantidad.desde(cantidadPendiente),
)

fun OrdenDto.aDominio() = Orden(
    id = id, numero = numero, proveedor = proveedor.aDominio(), estado = EstadoOrden.desde(estado),
    fechaEsperada = fechaEsperada?.let(LocalDate::parse), moneda = Moneda(moneda), notas = notas, motivoCierre = motivoCierre,
    lineas = lineas.map { it.aDominio() }, totalEstimado = totalEstimado?.aDominio(), creadaEn = instanteDesde(createdAt),
)

fun LineaRecepcionDto.aDominio() = LineaRecepcion(
    id, producto.aDominio(), ordenLineaId, Cantidad.desde(cantidadRecibida), costoUnitario.aDominio(), tasaCambio, costoUnitarioBase?.aDominio(), exceso,
)

fun RecepcionDto.aDominio() = Recepcion(
    id = id, numero = numero, proveedor = proveedor.aDominio(), orden = orden?.aDominio(), estado = EstadoRecepcion.desde(estado),
    fecha = LocalDate.parse(fecha), moneda = Moneda(moneda), tasaCambio = tasaCambio, notas = notas, lineas = lineas.map { it.aDominio() },
    total = total.aDominio(), totalBase = totalBase?.aDominio(), creadaEn = instanteDesde(createdAt),
)

fun RecepcionBreveDto.aDominio() = RecepcionBreve(id, numero, LocalDate.parse(fecha), total.aDominio())

fun ImagenDto.aDominio() = Imagen(id, url, ancho, alto, bytes)

fun FacturaDto.aDominio() = Factura(
    id = id, proveedor = proveedor.aDominio(), numero = numero, fechaEmision = LocalDate.parse(fechaEmision),
    fechaVencimiento = fechaVencimiento?.let(LocalDate::parse), moneda = Moneda(moneda), tasaCambio = tasaCambio,
    baseGravable = baseGravable.aDominio(), impuesto = impuesto.aDominio(), total = total.aDominio(), totalBase = totalBase.aDominio(),
    estadoPago = EstadoPago.desde(estadoPago), fechaPago = fechaPago?.let(LocalDate::parse), motivoAnulacion = motivoAnulacion, notas = notas,
    recepciones = recepciones.map { it.aDominio() }, imagenes = imagenes.map { it.aDominio() }, creadaEn = instanteDesde(createdAt),
)

fun FilaBajoMinimoDto.aDominio() = FilaBajoMinimo(producto.aDominio(), Cantidad.desde(stockActual), Cantidad.desde(stockMinimo), Cantidad.desde(deficit), deficitRelativo)

fun FilaAgotadoDto.aDominio() = FilaAgotado(producto.aDominio(), Cantidad.desde(stockActual), stockMinimo?.let(Cantidad::desde))

fun FilaSinMovimientoDto.aDominio() =
    FilaSinMovimiento(producto.aDominio(), Cantidad.desde(stockActual), valorACosto?.aDominio(), ultimoMovimientoEn?.let(::instanteDesde), instanteDesde(creadoEn))

fun ValorCategoriaDto.aDominio() = ValorCategoria(categoria?.aDominio(), productos, valor.aDominio())

fun FilaNoValorizableDto.aDominio() = FilaNoValorizable(producto.aDominio(), Cantidad.desde(stockActual))

fun ValorizacionDto.aDominio() = Valorizacion(total.aDominio(), productosValorizados, porCategoria.map { it.aDominio() }, noValorizables.aDominio { it.aDominio() })

fun ComprasProveedorDto.aDominio() = ComprasProveedor(proveedor.aDominio(), totalRecibido.aDominio(), totalFacturado.aDominio())

fun ComprasCategoriaDto.aDominio() = ComprasCategoria(categoria?.aDominio(), totalRecibido.aDominio())

fun ResumenComprasDto.aDominio() = ResumenCompras(
    desde, hasta, totalRecibido.aDominio(), totalFacturado.aDominio(), recepciones, facturas,
    porProveedor.map { it.aDominio() }, porCategoria.map { it.aDominio() },
)

fun MermaMotivoDto.aDominio() = MermaMotivo(motivo, etiqueta, Cantidad.desde(cantidad), valor.aDominio())

fun MermaProductoDto.aDominio() = MermaProducto(producto.aDominio(), Cantidad.desde(cantidad), valor.aDominio())

fun ResumenMermasDto.aDominio() =
    ResumenMermas(desde, hasta, Cantidad.desde(totalCantidad), totalValor.aDominio(), porMotivo.map { it.aDominio() }, porProducto.aDominio { it.aDominio() })

fun FilaDiscrepanciaDto.aDominio() = FilaDiscrepancia(
    movimientoId, producto.aDominio(), TipoMovimiento.desde(tipo), Cantidad.desde(cantidad), Cantidad.desde(stockResultante),
    motivo, nota, instanteDesde(ocurridoEn), autorTipo,
)

fun ApiKeyDto.aDominio() = ApiKey(id, nombre, prefijo, instanteDesde(createdAt), ultimoUsoEn?.let(::instanteDesde), revocadoEn?.let(::instanteDesde))

fun SuscripcionDto.aDominio() = Suscripcion(id, url, tipos, activa, descripcion, instanteDesde(createdAt))
