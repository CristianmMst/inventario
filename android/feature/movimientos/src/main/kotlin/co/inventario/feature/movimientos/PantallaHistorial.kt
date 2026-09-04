package co.inventario.feature.movimientos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonTexto
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.FilaMovimiento
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.SentidoMovimiento
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.TipoMovimiento
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * T-087: historial (RF-INV-012) y anulación con motivo (RF-INV-008). No hay forma de editar ni
 * de borrar, y no la habrá: un movimiento registrado es inmutable (RF-INV-007). Lo único que
 * cabe es anularlo con un contramovimiento, y eso se dice en el diálogo.
 */
@Composable
fun PantallaHistorial(
    productoId: String,
    alVolver: () -> Unit,
    vm: HistorialViewModel = hiltViewModel<HistorialViewModel, HistorialViewModel.Fabrica>(creationCallback = { it.crear(productoId) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    PantallaInventario(titulo = "Historial", alVolver = alVolver) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            val error = estado.error
            when {
                error != null && estado.movimientos.isEmpty() ->
                    EstadoError(error, vm::recargar, Modifier.weight(1f))

                estado.cargando && estado.movimientos.isEmpty() ->
                    EsqueletoLista(Modifier.weight(1f))

                estado.movimientos.isEmpty() -> EstadoVacio(
                    titulo = "Todavía sin movimientos",
                    explicacion = "Cuando registres una entrada o una salida de este producto, aparecerá aquí.",
                    icono = Iconos.historial,
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(estado.movimientos, key = { _, m -> m.id }) { indice, movimiento ->
                        if (indice >= estado.movimientos.size - 5) {
                            LaunchedEffect(indice) { vm.cargarMas() }
                        }
                        Column {
                            FilaMovimiento(
                                sentido = movimiento.tipo.aSentido(),
                                titulo = etiqueta(movimiento.tipo) + if (movimiento.forzado) " · forzado" else "",
                                detalle = detalleDe(movimiento, formato.format(movimiento.ocurridoEn)),
                                cantidad = movimiento.cantidadConSigno(),
                                stockResultante = movimiento.stockResultante.valor.stripTrailingZeros().toPlainString(),
                                anulado = movimiento.anulado,
                            )
                            if (vm.sePuedeAnular(movimiento)) {
                                BotonTexto(
                                    "Anular",
                                    { vm.pedirAnulacion(movimiento.id) },
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = Dimensiones.espacioCompacto),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (estado.cargando) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(Dimensiones.espacio), Alignment.Center) {
                                Text(
                                    "Cargando más…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (estado.anulando != null) {
        DialogoConfirmacion(
            titulo = "Anular movimiento",
            texto = "Se creará un contramovimiento que deshace este. El original queda marcado como anulado; " +
                "nada se borra.",
            textoConfirmar = "Anular",
            alConfirmar = vm::confirmarAnulacion,
            alCancelar = vm::cancelarAnulacion,
            destructivo = true,
            contenidoExtra = {
                CampoTexto(
                    valor = estado.nota,
                    alCambiar = vm::cambiarNota,
                    etiqueta = "Motivo de la anulación",
                    error = estado.erroresCampo["nota"],
                )
            },
        )
    }
}

private fun detalleDe(movimiento: Movimiento, fecha: String): String = listOfNotNull(
    movimiento.motivo,
    fecha,
    movimiento.nota?.takeIf { it.isNotBlank() },
    "Anula un movimiento anterior".takeIf { movimiento.anulaMovimientoId != null },
).joinToString(" · ")

private fun Movimiento.cantidadConSigno(): String {
    val cantidad = this.cantidad.valor.stripTrailingZeros().toPlainString()
    return if (direccion < 0) "−$cantidad" else "+$cantidad"
}

private fun TipoMovimiento.aSentido(): SentidoMovimiento = when (this) {
    TipoMovimiento.ENTRADA -> SentidoMovimiento.ENTRADA
    TipoMovimiento.SALIDA -> SentidoMovimiento.SALIDA
    TipoMovimiento.MERMA -> SentidoMovimiento.MERMA
    TipoMovimiento.AJUSTE -> SentidoMovimiento.AJUSTE
    TipoMovimiento.CONTRAMOVIMIENTO -> SentidoMovimiento.ANULACION
}

private fun etiqueta(tipo: TipoMovimiento) = when (tipo) {
    TipoMovimiento.ENTRADA -> "Entrada"
    TipoMovimiento.SALIDA -> "Salida"
    TipoMovimiento.MERMA -> "Merma"
    TipoMovimiento.AJUSTE -> "Ajuste"
    TipoMovimiento.CONTRAMOVIMIENTO -> "Anulación"
}
