package co.inventario.feature.ajustes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones

/** T-095: credenciales de servicio (RF-AUT-005) y webhooks (RF-INT-005). El secreto se ve una vez. */
@Composable
fun PantallaAjustes(alCerrarSesion: () -> Unit, alVolver: () -> Unit, vm: AjustesViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)
        MensajeError(estado.error)

        Text("Credenciales de servicio (API keys)", style = MaterialTheme.typography.titleMedium)
        Text("Para conectar otros sistemas. Autorizan lo mismo que tu sesión, solo en este negocio.", style = MaterialTheme.typography.bodyLarge)
        estado.claves.forEach { k ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${k.nombre} · ${k.prefijo}…", style = MaterialTheme.typography.bodyLarge)
                    Text("Creada ${k.creadaEn.toString().take(10)}" + (k.ultimoUsoEn?.let { " · último uso ${it.toString().take(10)}" } ?: " · sin uso"), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { vm.revocarClave(k.id) }) { Text("Revocar") }
            }
            HorizontalDivider()
        }
        CampoTexto(estado.nombreClave, vm::cambiarNombreClave, "Nombre de la nueva credencial", error = estado.erroresCampo["nombreClave"])
        BotonSecundario("Crear credencial", vm::crearClave)

        Text("Webhooks", style = MaterialTheme.typography.titleMedium)
        Text("Se guardan las suscripciones; la entrega de eventos llegará en una versión posterior.", style = MaterialTheme.typography.bodyLarge)
        estado.suscripciones.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(s.url, style = MaterialTheme.typography.bodyLarge)
                    Text(s.tipos.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { vm.eliminarSuscripcion(s.id) }) { Text("Quitar") }
            }
            HorizontalDivider()
        }
        CampoTexto(estado.urlWebhook, vm::cambiarUrlWebhook, "URL de destino (https)", error = estado.erroresCampo["url"])
        CampoTexto(estado.secretoWebhook, vm::cambiarSecretoWebhook, "Secreto de firma (32+ caracteres)", error = estado.erroresCampo["secreto"], esContrasena = true)
        CampoTexto(estado.descripcionWebhook, vm::cambiarDescripcionWebhook, "Descripción (opcional)")
        Text("Eventos", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            estado.tiposDisponibles.forEach { t -> FilterChip(selected = t in estado.tiposElegidos, onClick = { vm.alternarTipo(t) }, label = { Text(t) }) }
        }
        estado.erroresCampo["tipos"]?.let { MensajeError(it) }
        BotonSecundario("Crear suscripción", vm::crearSuscripcion)

        HorizontalDivider()
        BotonSecundario("Cerrar sesión", alCerrarSesion)
        BotonSecundario("Volver", alVolver)
    }

    estado.secretoNuevo?.let { secreto ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Copia el secreto ahora") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    Text("Este secreto se muestra una sola vez. Si lo pierdes, revoca la credencial y crea otra.", style = MaterialTheme.typography.bodyLarge)
                    Text(secreto, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    (contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("API key", secreto))
                }) { Text("Copiar") }
            },
            dismissButton = { TextButton(onClick = vm::secretoGuardado) { Text("Ya lo guardé") } },
        )
    }
}
