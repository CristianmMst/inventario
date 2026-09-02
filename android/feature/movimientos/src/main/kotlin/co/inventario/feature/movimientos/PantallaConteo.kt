package co.inventario.feature.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.StockDestacado
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Conteo
import co.inventario.domain.modelo.TipoUnidad

/** T-086: conteo físico (RF-INV-013): la diferencia se ve antes de confirmar. */
@Composable
fun PantallaConteo(
    productoId: String,
    alRegistrar: (Conteo) -> Unit,
    alCancelar: () -> Unit,
    vm: ConteoViewModel = hiltViewModel<ConteoViewModel, ConteoViewModel.Fabrica>(creationCallback = { it.crear(productoId) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is ConteoSuceso.Registrado) alRegistrar(it.conteo) } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Conteo físico", style = MaterialTheme.typography.headlineMedium)
        estado.producto?.let { p ->
            Text(p.nombre, style = MaterialTheme.typography.titleMedium)
            StockDestacado(estado.stockActual, "${p.unidad.nombre} según el sistema")
        }
        CampoCantidad(
            estado.contada, vm::cambiarContada, "Cantidad contada",
            error = estado.erroresCampo["contada"],
            admiteDecimales = estado.producto?.unidad?.tipo != TipoUnidad.DISCRETA,
        )
        estado.diferencia?.let { d ->
            val texto = when {
                d == "0" -> "Coincide con el sistema."
                d.startsWith("-") -> "Faltan ${d.removePrefix("-")}: se registrará un ajuste que resta."
                else -> "Sobran $d: se registrará un ajuste que suma."
            }
            Text(texto, style = MaterialTheme.typography.titleMedium, color = if (d == "0") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        CampoTexto(estado.nota, vm::cambiarNota, "Nota (obligatoria): dónde y cómo contaste", error = estado.erroresCampo["nota"])
        estado.aviso?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Guardando…" else "Confirmar el conteo", vm::confirmar, habilitado = !estado.cargando)
        BotonSecundario("Cancelar", alCancelar, habilitado = !estado.cargando)
    }
}
