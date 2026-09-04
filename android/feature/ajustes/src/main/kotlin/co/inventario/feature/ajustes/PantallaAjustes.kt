package co.inventario.feature.ajustes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.BotonTexto
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia

/** T-095: credenciales de servicio (RF-AUT-005) y webhooks (RF-INT-005). El secreto se ve una vez. */
@Composable
fun PantallaAjustes(alCerrarSesion: () -> Unit, alVolver: () -> Unit, vm: AjustesViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var claveARevocar by remember { mutableStateOf<Pair<String, String>?>(null) }

    PantallaInventario(titulo = "Ajustes e integraciones", alVolver = alVolver) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioAmplio),
        ) {
            MensajeError(estado.error)

            Seccion(
                titulo = "Credenciales de servicio",
                explicacion = "Para conectar otros sistemas. Autorizan lo mismo que tu sesión, solo en este negocio.",
            ) {
                estado.claves.forEach { clave ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
                        ) {
                            Text("${clave.nombre} · ${clave.prefijo}…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Creada ${clave.creadaEn.toString().take(10)}" +
                                    (clave.ultimoUsoEn?.let { " · último uso ${it.toString().take(10)}" } ?: " · sin uso"),
                                style = Tipografia.numeroCuerpo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BotonTexto(
                            "Revocar",
                            { claveARevocar = clave.id to clave.nombre },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                CampoTexto(
                    estado.nombreClave,
                    vm::cambiarNombreClave,
                    "Nombre de la nueva credencial",
                    error = estado.erroresCampo["nombreClave"],
                )
                BotonSecundario("Crear credencial", vm::crearClave, icono = Iconos.anadir)
            }

            Seccion(
                titulo = "Webhooks",
                explicacion = "Se guardan las suscripciones; la entrega de eventos llegará en una versión posterior.",
            ) {
                estado.suscripciones.forEach { suscripcion ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
                        ) {
                            Text(suscripcion.url, style = MaterialTheme.typography.titleMedium)
                            Text(
                                suscripcion.tipos.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BotonTexto(
                            "Quitar",
                            { vm.eliminarSuscripcion(suscripcion.id) },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                CampoTexto(estado.urlWebhook, vm::cambiarUrlWebhook, "URL de destino (https)", error = estado.erroresCampo["url"])
                CampoTexto(
                    estado.secretoWebhook,
                    vm::cambiarSecretoWebhook,
                    "Secreto de firma",
                    error = estado.erroresCampo["secreto"],
                    esContrasena = true,
                    apoyo = "Mínimo 32 caracteres. Sirve para que tu sistema compruebe que el aviso vino de aquí.",
                )
                CampoTexto(estado.descripcionWebhook, vm::cambiarDescripcionWebhook, "Descripción (opcional)")

                Text(
                    "Eventos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                ) {
                    estado.tiposDisponibles.forEach { tipo ->
                        ChipFiltro(
                            texto = tipo,
                            activo = tipo in estado.tiposElegidos,
                            alPulsar = { vm.alternarTipo(tipo) },
                        )
                    }
                }
                estado.erroresCampo["tipos"]?.let { MensajeError(it) }
                BotonSecundario("Crear suscripción", vm::crearSuscripcion, icono = Iconos.anadir)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BotonSecundario(
                "Cerrar sesión",
                alCerrarSesion,
                icono = Iconos.cerrarSesion,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    claveARevocar?.let { (id, nombre) ->
        DialogoConfirmacion(
            titulo = "¿Revocar «$nombre»?",
            texto = "Cualquier sistema que use esta credencial dejará de funcionar inmediatamente. " +
                "No se puede volver a activar: habría que crear otra.",
            textoConfirmar = "Revocar",
            alConfirmar = {
                vm.revocarClave(id)
                claveARevocar = null
            },
            alCancelar = { claveARevocar = null },
            destructivo = true,
        )
    }

    // El secreto se muestra una sola vez y no hay forma de recuperarlo: el diálogo no se cierra
    // tocando fuera, y lo dice antes de enseñarlo.
    estado.secretoNuevo?.let { secreto ->
        DialogoConfirmacion(
            titulo = "Copia el secreto ahora",
            texto = "Este secreto se muestra una sola vez. Si lo pierdes, hay que revocar la credencial y crear otra.",
            textoConfirmar = "Copiar",
            alConfirmar = {
                val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                portapapeles.setPrimaryClip(ClipData.newPlainText("API key", secreto))
            },
            alCancelar = vm::secretoGuardado,
            destructivo = true,
            textoCancelar = "Ya lo guardé",
            contenidoExtra = {
                Text(
                    secreto,
                    style = Tipografia.numeroCuerpo,
                    color = Estado.sobreBajoMinimoContenedor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Estado.bajoMinimoContenedor, MaterialTheme.shapes.small)
                        .padding(Dimensiones.espacioMedio),
                )
            },
        )
    }
}

@Composable
private fun Seccion(titulo: String, explicacion: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            explicacion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        contenido()
    }
}
