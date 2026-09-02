package co.inventario.feature.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
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
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones

/** T-080: entre el escaneo y la ficha. Código desconocido → alta precargada, con decisión explícita. */
@Composable
fun PantallaResolverCodigo(
    codigo: String,
    alAbrirFicha: (String) -> Unit,
    alCrearConCodigo: (String) -> Unit,
    alVolver: () -> Unit,
    vm: ResolverCodigoViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(codigo) { vm.resolver(codigo) }
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is ResolucionSuceso.AbrirFicha) alAbrirFicha(it.productoId) } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Código $codigo", style = MaterialTheme.typography.headlineMedium)
        when {
            estado.cargando -> CircularProgressIndicator()
            estado.codigoDesconocido != null -> {
                Text(estado.mensaje.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                BotonPrincipal("Crear producto con este código", { alCrearConCodigo(estado.codigoDesconocido!!) })
                BotonSecundario("Volver a escanear", alVolver)
            }
            estado.error != null -> {
                MensajeError(estado.error?.mensaje)
                BotonPrincipal("Reintentar", { vm.resolver(codigo) })
                BotonSecundario("Volver", alVolver)
            }
        }
    }
}
