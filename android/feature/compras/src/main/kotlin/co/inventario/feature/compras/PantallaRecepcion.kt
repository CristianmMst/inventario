package co.inventario.feature.compras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.FilaDato
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.TarjetaDatos
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
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

    PantallaInventario(
        titulo = estado.ordenNumero?.let { "Recibir $it" } ?: "Recepción directa",
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Confirmando…" else "Confirmar recepción",
                        vm::confirmar,
                        habilitado = !estado.cargando,
                        icono = Iconos.confirmar,
                    )
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
            if (estado.ordenId == null) {
                SelectorProveedor(
                    estado.proveedores,
                    estado.proveedorId,
                    vm::elegirProveedor,
                    estado.erroresCampo["proveedor"],
                )
            } else {
                TarjetaDatos {
                    FilaDato(
                        "Proveedor",
                        estado.proveedores.firstOrNull { it.id == estado.proveedorId }?.nombre.orEmpty(),
                        ultima = true,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                CampoTexto(
                    estado.moneda,
                    vm::cambiarMoneda,
                    "Moneda",
                    apoyo = "Código ISO 4217. Si no es $monedaBase, hay que decir a cuánto está el cambio.",
                )
                if (estado.moneda != monedaBase) {
                    CampoCantidad(
                        estado.tasaCambio,
                        vm::cambiarTasa,
                        "Tasa de cambio a $monedaBase",
                        error = estado.erroresCampo["tasaCambio"],
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                Text("Líneas recibidas", style = MaterialTheme.typography.titleMedium)
                estado.lineas.forEachIndexed { indice, linea ->
                    LineaEditable(
                        linea.producto,
                        linea.cantidad,
                        linea.costo,
                        "Costo unitario (${estado.moneda})",
                        linea.pendiente,
                        estado.erroresCampo["cantidad_$indice"],
                        estado.erroresCampo["costo_$indice"],
                        { vm.cambiarCantidad(indice, it) },
                        { vm.cambiarCosto(indice, it) },
                        { vm.quitarLinea(indice) },
                    )
                }
                estado.erroresCampo["lineas"]?.let { MensajeError(it) }
                if (estado.ordenId == null) SelectorProducto(alElegir = vm::agregarLinea)
            }

            CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)", lineas = 3)
            MensajeError(estado.error)
        }
    }

    estado.avisoExceso?.let { aviso ->
        DialogoConfirmacion(
            titulo = "Estás recibiendo más de lo pedido",
            texto = "La orden ${aviso.ordenNumero.orEmpty()} pedía menos en ${aviso.lineasConExceso} línea(s). " +
                "Si confirmas, el exceso queda registrado en la recepción.",
            textoConfirmar = "Sí, recibir el exceso",
            alConfirmar = vm::confirmarExceso,
            alCancelar = vm::descartarExceso,
            textoCancelar = "Corregir cantidades",
        )
    }
}
