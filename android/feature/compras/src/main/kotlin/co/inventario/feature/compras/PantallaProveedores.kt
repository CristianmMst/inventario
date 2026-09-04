package co.inventario.feature.compras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.BotonTexto
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.PildoraEstado
import co.inventario.designsystem.componentes.EstadoStock
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos

/** T-088: proveedores (RF-COM-001). Con documentos no se borra: se archiva (RN-17). */
@Composable
fun PantallaProveedores(alVolver: () -> Unit, vm: ProveedoresViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val formulario = estado.formulario

    // El formulario se abre encima del listado dentro de la misma ruta; sin esto, «atrás»
    // abandonaba la pantalla entera en vez de cerrar el formulario.
    BackHandler(enabled = formulario != null) { vm.cerrarFormulario() }

    if (formulario != null) {
        FormularioProveedor(estado, formulario, vm)
    } else {
        ListadoProveedores(estado, vm, alVolver)
    }
}

@Composable
private fun FormularioProveedor(
    estado: ProveedoresUiState,
    formulario: FormularioProveedor,
    vm: ProveedoresViewModel,
) {
    PantallaInventario(
        titulo = if (formulario.id == null) "Nuevo proveedor" else "Editar proveedor",
        alVolver = vm::cerrarFormulario,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", vm::cerrarFormulario, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Guardando…" else "Guardar",
                        vm::guardar,
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
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            CAMPOS.forEach { (campo, etiqueta) ->
                CampoTexto(
                    valor = formulario.campos[campo].orEmpty(),
                    alCambiar = { vm.cambiarCampo(campo, it) },
                    etiqueta = etiqueta,
                    error = estado.erroresCampo[campo],
                    tipoTeclado = when (campo) {
                        "telefono" -> KeyboardType.Phone
                        "email" -> KeyboardType.Email
                        else -> KeyboardType.Text
                    },
                    habilitado = !estado.cargando,
                    lineas = if (campo == "notas") 3 else 1,
                )
            }
            MensajeError(estado.error)
        }
    }
}

private val CAMPOS = listOf(
    "nombre" to "Nombre",
    "identificacionFiscal" to "Identificación fiscal (NIT)",
    "contacto" to "Persona de contacto",
    "telefono" to "Teléfono",
    "email" to "Correo",
    "direccion" to "Dirección",
    "notas" to "Notas",
)

@Composable
private fun ListadoProveedores(
    estado: ProveedoresUiState,
    vm: ProveedoresViewModel,
    alVolver: () -> Unit,
) {
    var porEliminar by remember { mutableStateOf<Pair<String, String>?>(null) }

    PantallaInventario(
        titulo = "Proveedores",
        alVolver = alVolver,
        acciones = {
            BotonPrincipal("Nuevo proveedor", vm::nuevo, icono = Iconos.anadir)
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            Row(
                Modifier.padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioCompacto),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            ) {
                ChipFiltro(
                    texto = "Ver archivados",
                    activo = estado.incluirArchivados,
                    alPulsar = vm::alternarArchivados,
                )
            }
            MensajeError(estado.error)
            estado.sugerirArchivar?.let { id ->
                BotonSecundario(
                    "Archivar este proveedor",
                    { vm.archivar(id) },
                    Modifier.padding(horizontal = Dimensiones.espacio),
                    icono = Iconos.archivar,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                estado.cargando && estado.proveedores.isEmpty() -> EsqueletoLista(Modifier.weight(1f))
                estado.proveedores.isEmpty() -> EstadoVacio(
                    titulo = "Todavía no hay proveedores",
                    explicacion = "Registra a quién le compras para poder recibir mercancía y guardar facturas.",
                    icono = Iconos.proveedor,
                    textoAccion = "Crear el primero",
                    alAccionar = vm::nuevo,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(estado.proveedores, key = { it.id }) { proveedor ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
                                .clickable { vm.editar(proveedor.id) }
                                .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
                            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                            ) {
                                Text(
                                    proveedor.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (proveedor.archivado) {
                                    PildoraEstado(EstadoStock.ARCHIVADO)
                                }
                            }
                            val detalle = listOfNotNull(
                                proveedor.identificacionFiscal,
                                proveedor.telefono,
                                proveedor.email,
                            ).joinToString(" · ")
                            if (detalle.isNotBlank()) {
                                Text(
                                    detalle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                                if (proveedor.archivado) {
                                    BotonTexto("Desarchivar", { vm.archivar(proveedor.id, archivar = false) })
                                } else {
                                    BotonTexto("Archivar", { vm.archivar(proveedor.id) })
                                    BotonTexto(
                                        "Eliminar",
                                        { porEliminar = proveedor.id to proveedor.nombre },
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // Antes esto borraba al primer toque, sin preguntar y sin deshacer.
    porEliminar?.let { (id, nombre) ->
        DialogoConfirmacion(
            titulo = "¿Eliminar a $nombre?",
            texto = "Se borra de la lista y no se puede deshacer. Si ya tiene órdenes, recepciones o " +
                "facturas, el servidor no dejará borrarlo: archívalo en su lugar.",
            textoConfirmar = "Eliminar",
            alConfirmar = {
                vm.eliminar(id)
                porEliminar = null
            },
            alCancelar = { porEliminar = null },
            destructivo = true,
        )
    }
}
