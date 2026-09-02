package co.inventario.app.navegacion

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.data.repositorio.RepositorioSesion
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.tema.Dimensiones
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cierre de sesión desde Ajustes (RF-AUT-003): borra tokens y avisa al servidor. */
@HiltViewModel
class SesionViewModel @Inject constructor(private val sesion: RepositorioSesion) : ViewModel() {
    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            sesion.cerrarSesion()
            alTerminar()
        }
    }
}

/** Menú de compras, facturas, reportes y ajustes. El escaneo sigue siendo la puerta de entrada (HU-03). */
@Composable
fun PantallaMenu(nombreNegocio: String, irA: (Ruta) -> Unit, alVolver: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text(nombreNegocio, style = MaterialTheme.typography.headlineMedium)
        Text("Compras", style = MaterialTheme.typography.titleMedium)
        BotonSecundario("Recibir mercancía", { irA(Ruta.Recepcion()) })
        BotonSecundario("Órdenes de compra", { irA(Ruta.Ordenes) })
        BotonSecundario("Proveedores", { irA(Ruta.Proveedores) })
        Text("Documentos", style = MaterialTheme.typography.titleMedium)
        BotonSecundario("Facturas de compra", { irA(Ruta.Facturas) })
        BotonSecundario("Reportes", { irA(Ruta.Reportes) })
        Text("Cuenta", style = MaterialTheme.typography.titleMedium)
        BotonSecundario("Ajustes e integraciones", { irA(Ruta.Ajustes) })
        BotonSecundario("Volver al escaneo", alVolver)
    }
}
