package co.inventario.feature.compras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones

/** T-088: proveedores (RF-COM-001). Con documentos no se borra: se archiva (RN-17). */
@Composable
fun PantallaProveedores(alVolver: () -> Unit, vm: ProveedoresViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val formulario = estado.formulario
    if (formulario != null) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            Text(if (formulario.id == null) "Nuevo proveedor" else "Editar proveedor", style = MaterialTheme.typography.headlineMedium)
            listOf(
                "nombre" to "Nombre", "identificacionFiscal" to "Identificación fiscal (NIT)", "contacto" to "Persona de contacto",
                "telefono" to "Teléfono", "email" to "Correo", "direccion" to "Dirección", "notas" to "Notas",
            ).forEach { (campo, etiqueta) ->
                CampoTexto(
                    formulario.campos[campo].orEmpty(), { vm.cambiarCampo(campo, it) }, etiqueta,
                    error = estado.erroresCampo[campo],
                    tipoTeclado = when (campo) { "telefono" -> KeyboardType.Phone; "email" -> KeyboardType.Email; else -> KeyboardType.Text },
                    habilitado = !estado.cargando,
                )
            }
            MensajeError(estado.error)
            BotonPrincipal(if (estado.cargando) "Guardando…" else "Guardar", vm::guardar, habilitado = !estado.cargando)
            BotonSecundario("Cancelar", vm::cerrarFormulario)
        }
        return
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        Text("Proveedores", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Dimensiones.espacio))
        MensajeError(estado.error)
        estado.sugerirArchivar?.let { id -> BotonSecundario("Archivar este proveedor", { vm.archivar(id) }) }
        LazyColumn(Modifier.weight(1f)) {
            items(estado.proveedores, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima).clickable { vm.editar(p.id) }.padding(vertical = Dimensiones.espacioCompacto)) {
                    Text(p.nombre + if (p.archivado) " · archivado" else "", style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(p.identificacionFiscal, p.telefono, p.email).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                    Row {
                        if (p.archivado) {
                            TextButton(onClick = { vm.archivar(p.id, archivar = false) }) { Text("Desarchivar") }
                        } else {
                            TextButton(onClick = { vm.archivar(p.id) }) { Text("Archivar") }
                            TextButton(onClick = { vm.eliminar(p.id) }) { Text("Eliminar") }
                        }
                    }
                }
                HorizontalDivider()
            }
            if (!estado.cargando && estado.proveedores.isEmpty()) {
                item { Text("Todavía no hay proveedores.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimensiones.espacio)) }
            }
        }
        BotonSecundario(if (estado.incluirArchivados) "Ocultar archivados" else "Ver archivados", vm::alternarArchivados)
        BotonPrincipal("Nuevo proveedor", vm::nuevo)
        BotonSecundario("Volver", alVolver, Modifier.padding(bottom = Dimensiones.espacio))
    }
}
