package co.inventario.feature.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.SelectorCantidad
import co.inventario.designsystem.tema.Colores
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.modelo.TipoUnidad
import java.math.BigDecimal

/**
 * T-084 / T-085: registrar entrada, salida, merma o ajuste con la lista cerrada de motivos.
 *
 * Antes lo único que distinguía una entrada de una merma era el texto del título. Ahora cada
 * tipo tiene su color e icono, la cantidad se ajusta con pasos de uno sin abrir el teclado, y
 * antes de confirmar se ve **en cuánto va a quedar** el stock: es exactamente el número que
 * Marta va a comparar con el estante.
 */
@Composable
fun PantallaMovimiento(
    productoId: String,
    tipo: TipoMovimiento,
    alRegistrar: (Movimiento) -> Unit,
    alCancelar: () -> Unit,
    vm: MovimientoViewModel = hiltViewModel<MovimientoViewModel, MovimientoViewModel.Fabrica>(
        creationCallback = { it.crear(productoId, tipo) },
    ),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is MovimientoSuceso.Registrado) alRegistrar(it.movimiento) } }

    val aspecto = aspectoDe(tipo)

    PantallaInventario(
        titulo = titulo(tipo),
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    if (estado.pendiente != null) {
                        BotonPrincipal(
                            "Reintentar ahora",
                            vm::reintentar,
                            habilitado = !estado.cargando,
                            icono = Iconos.reintentar,
                        )
                    } else {
                        BotonPrincipal(
                            if (estado.cargando) "Guardando…" else "Confirmar",
                            vm::confirmar,
                            habilitado = !estado.cargando,
                            icono = Iconos.confirmar,
                        )
                    }
                }
            }
        },
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioAmplio),
        ) {
            estado.producto?.let { producto ->
                CabeceraTipo(aspecto, producto.nombre)
                Resultado(
                    stockActual = producto.stockActual.valor,
                    cantidad = estado.cantidad,
                    direccion = direccionEfectiva(tipo, estado.direccion),
                    unidad = producto.unidad.nombre,
                )
            }

            SelectorCantidad(
                valor = estado.cantidad,
                alCambiar = vm::cambiarCantidad,
                unidad = "Cantidad",
                error = estado.erroresCampo["cantidad"],
                admiteDecimales = estado.producto?.unidad?.tipo != TipoUnidad.DISCRETA,
                habilitado = !estado.cargando,
            )

            if (tipo == TipoMovimiento.AJUSTE) {
                Grupo("El ajuste…") {
                    ChipFiltro(texto = "Suma", activo = estado.direccion == 1, alPulsar = { vm.cambiarDireccion(1) })
                    ChipFiltro(texto = "Resta", activo = estado.direccion == -1, alPulsar = { vm.cambiarDireccion(-1) })
                }
                estado.erroresCampo["direccion"]?.let { MensajeError(it) }
            }

            Grupo("Motivo") {
                estado.motivos.forEach { motivo ->
                    ChipFiltro(
                        texto = motivo.etiqueta,
                        activo = motivo.codigo == estado.motivo,
                        alPulsar = { vm.cambiarMotivo(motivo.codigo) },
                    )
                }
            }
            estado.erroresCampo["motivo"]?.let { MensajeError(it) }

            CampoTexto(
                valor = estado.nota,
                alCambiar = vm::cambiarNota,
                etiqueta = if (estado.notaObligatoria) "Nota (obligatoria)" else "Nota (opcional)",
                error = estado.erroresCampo["nota"],
                apoyo = if (estado.notaObligatoria) "Este motivo exige explicar qué pasó." else null,
            )

            MensajeError(estado.error)
        }
    }

    estado.override?.let { o ->
        val unidad = estado.producto?.unidad?.nombre.orEmpty()
        val disponible = o.disponible.valor.stripTrailingZeros().toPlainString()
        val solicitado = o.solicitado.valor.stripTrailingZeros().toPlainString()
        DialogoConfirmacion(
            titulo = "No alcanza el stock",
            texto = "Hay $disponible $unidad y estás sacando $solicitado.",
            textoConfirmar = if (o.puedeForzar) "Forzar de todos modos" else "Entendido",
            alConfirmar = { if (o.puedeForzar) vm.forzar() else vm.cancelarOverride() },
            alCancelar = vm::cancelarOverride,
            destructivo = o.puedeForzar,
            contenidoExtra = if (o.puedeForzar) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                        Text(
                            "El stock quedará en negativo y el movimiento se marcará como forzado. Escribe por qué.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CampoTexto(
                            valor = estado.nota,
                            alCambiar = vm::cambiarNota,
                            etiqueta = "Motivo del forzado",
                            error = estado.erroresCampo["nota"],
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

/** Color e icono por tipo; hasta ahora solo cambiaba el texto del título. */
private data class AspectoMovimiento(
    val icono: ImageVector,
    val color: Color,
    val contenedor: Color,
    val sobreContenedor: Color,
    val frase: String,
)

private fun aspectoDe(tipo: TipoMovimiento): AspectoMovimiento = when (tipo) {
    TipoMovimiento.ENTRADA -> AspectoMovimiento(
        Iconos.entrada, Estado.enRango, Estado.enRangoContenedor, Estado.sobreEnRangoContenedor,
        "Entra al inventario",
    )
    TipoMovimiento.SALIDA -> AspectoMovimiento(
        Iconos.salida, Colores.terciario, Colores.terciarioContenedor, Colores.sobreTerciarioContenedor,
        "Sale del inventario",
    )
    TipoMovimiento.MERMA -> AspectoMovimiento(
        Iconos.merma, Estado.bajoMinimo, Estado.bajoMinimoContenedor, Estado.sobreBajoMinimoContenedor,
        "Se pierde: no se vendió",
    )
    TipoMovimiento.AJUSTE -> AspectoMovimiento(
        Iconos.conteo, Colores.secundario, Colores.secundarioContenedor, Colores.sobreSecundarioContenedor,
        "Corrige el stock del sistema",
    )
    TipoMovimiento.CONTRAMOVIMIENTO -> AspectoMovimiento(
        Iconos.anular, Estado.neutro, Estado.neutroContenedor, Estado.neutro,
        "Deshace un movimiento anterior",
    )
}

@Composable
private fun CabeceraTipo(aspecto: AspectoMovimiento, nombreProducto: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = aspecto.contenedor),
    ) {
        Row(
            Modifier.padding(Dimensiones.espacio),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Dimensiones.miniatura)
                    .background(aspecto.color, RoundedCornerShape(Dimensiones.radioPildora)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    aspecto.icono,
                    contentDescription = null,
                    tint = Colores.sobrePrimario,
                    modifier = Modifier.size(Dimensiones.icono),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
                Text(aspecto.frase, style = MaterialTheme.typography.titleMedium, color = aspecto.sobreContenedor)
                Text(nombreProducto, style = MaterialTheme.typography.bodyMedium, color = aspecto.sobreContenedor)
            }
        }
    }
}

/**
 * En cuánto va a quedar el stock. Es aritmética sobre lo que ya está en pantalla, no un dato
 * nuevo: sirve para que el número que Marta va a comparar con el estante se vea **antes** de
 * confirmar, no después.
 */
@Composable
private fun Resultado(stockActual: BigDecimal, cantidad: String, direccion: Int?, unidad: String) {
    if (direccion == null) return
    val pedida = cantidad.toBigDecimalOrNull() ?: return
    val resultante = stockActual.add(pedida.multiply(BigDecimal(direccion)))
    val negativo = resultante.signum() < 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .padding(Dimensiones.espacio),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
            Text(
                "Quedará en",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "ahora hay ${stockActual.stripTrailingZeros().toPlainString()}",
                style = Tipografia.numeroCuerpo,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                resultante.stripTrailingZeros().toPlainString(),
                style = Tipografia.stockSecundario,
                color = if (negativo) Estado.agotado else MaterialTheme.colorScheme.primary,
            )
            Text(
                unidad,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimensiones.espacioCompacto),
            )
        }
    }
}

/**
 * Salida y merma restan; entrada suma; el ajuste lo decide quien lo registra, y mientras no lo
 * haya elegido no se puede anticipar en cuánto quedará: se devuelve `null` y no se enseña nada,
 * antes que enseñar un número inventado.
 */
private fun direccionEfectiva(tipo: TipoMovimiento, direccionAjuste: Int?): Int? = when (tipo) {
    TipoMovimiento.ENTRADA -> 1
    TipoMovimiento.SALIDA, TipoMovimiento.MERMA -> -1
    TipoMovimiento.AJUSTE, TipoMovimiento.CONTRAMOVIMIENTO -> direccionAjuste
}

@Composable
private fun Grupo(etiqueta: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        ) {
            contenido()
        }
    }
}

private fun titulo(tipo: TipoMovimiento) = when (tipo) {
    TipoMovimiento.ENTRADA -> "Registrar entrada"
    TipoMovimiento.SALIDA -> "Registrar salida"
    TipoMovimiento.MERMA -> "Registrar merma"
    TipoMovimiento.AJUSTE -> "Registrar ajuste"
    TipoMovimiento.CONTRAMOVIMIENTO -> "Anulación"
}
