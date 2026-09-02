package co.inventario.feature.compras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Recepcion

/** T-090: recepción directa o contra orden (RF-COM-004/005); exceso con confirmación explícita (RF-COM-009). */
@Composable
fun PantallaRecepcion(
    ordenId: String?,
    monedaBase: String,
    alConfirmar: (Recepcion) -> Unit,
    alCancelar: () -> Unit,
    vm: RecepcionViewModel = hiltViewModel<RecepcionViewModel, RecepcionViewModel.Fabrica>(creationCallback = { it.crear(ordenId, monedaBase) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is RecepcionSuceso.Confirmada) alConfirmar(it.recepcion) } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text(estado.ordenNumero?.let { "Recibir $it" } ?: "Recepción directa", style = MaterialTheme.typography.headlineMedium)
        if (estado.ordenId == null) {
            SelectorProveedor(estado.proveedores, estado.proveedorId, vm::elegirProveedor, estado.erroresCampo["proveedor"])
        } else {
            Text("Proveedor: ${estado.proveedores.firstOrNull { it.id == estado.proveedorId }?.nombre ?: ""}", style = MaterialTheme.typography.bodyLarge)
        }
        CampoTexto(estado.moneda, vm::cambiarMoneda, "Moneda (ISO 4217)")
        if (estado.moneda != monedaBase) {
            CampoCantidad(estado.tasaCambio, vm::cambiarTasa, "Tasa de cambio a $monedaBase", error = estado.erroresCampo["tasaCambio"])
        }
        Text("Líneas recibidas", style = MaterialTheme.typography.titleMedium)
        estado.lineas.forEachIndexed { i, l ->
            LineaEditable(
                l.producto, l.cantidad, l.costo, "Costo unitario (${estado.moneda})", l.pendiente,
                estado.erroresCampo["cantidad_$i"], estado.erroresCampo["costo_$i"],
                { vm.cambiarCantidad(i, it) }, { vm.cambiarCosto(i, it) }, { vm.quitarLinea(i) },
            )
        }
        estado.erroresCampo["lineas"]?.let { MensajeError(it) }
        if (estado.ordenId == null) SelectorProducto(alElegir = vm::agregarLinea)
        CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)")
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Confirmando…" else "Confirmar recepción", vm::confirmar, habilitado = !estado.cargando)
        BotonSecundario("Cancelar", alCancelar, habilitado = !estado.cargando)
    }

    estado.avisoExceso?.let { aviso ->
        AlertDialog(
            onDismissRequest = vm::descartarExceso,
            title = { Text("Estás recibiendo más de lo pedido") },
            text = {
                Text(
                    "La orden ${aviso.ordenNumero ?: ""} pedía menos en ${aviso.lineasConExceso} línea(s). Si confirmas, el exceso queda registrado en la recepción.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmarExceso, enabled = !estado.cargando) { Text("Sí, recibir el exceso") } },
            dismissButton = { TextButton(onClick = vm::descartarExceso) { Text("Corregir cantidades") } },
        )
    }
}
