package co.inventario.app.navegacion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.data.repositorio.RepositorioSesion
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
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

/**
 * Compras, documentos y cuenta. Antes eran ocho botones idénticos en una columna: nada decía
 * qué hacía cada uno ni cuál importaba. Ahora cada entrada lleva su icono y una línea que dice
 * para qué sirve, y el destino ya no necesita un botón de «volver» porque vive en la barra.
 */
@Composable
fun PantallaMenu(
    nombreNegocio: String,
    moneda: String,
    irA: (Ruta) -> Unit,
    alCerrarSesion: () -> Unit,
    barraInferior: @Composable () -> Unit = {},
) {
    PantallaInventario(titulo = "Más", barraInferior = barraInferior) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState()),
        ) {
            CabeceraNegocio(nombreNegocio, moneda)

            Seccion("Compras")
            FilaMenu(
                icono = Iconos.reponer,
                titulo = "Recibir mercancía",
                subtitulo = "Llegó el proveedor",
                alPulsar = { irA(Ruta.Recepcion()) },
            )
            FilaMenu(
                icono = Iconos.orden,
                titulo = "Órdenes de compra",
                subtitulo = "Lo pedido y lo que falta por llegar",
                alPulsar = { irA(Ruta.Ordenes) },
            )
            FilaMenu(
                icono = Iconos.proveedor,
                titulo = "Proveedores",
                subtitulo = "A quién se le compra",
                alPulsar = { irA(Ruta.Proveedores) },
            )

            Separador()
            Seccion("Documentos")
            FilaMenu(
                icono = Iconos.factura,
                titulo = "Facturas de compra",
                subtitulo = "Registrar, pagar y exportar para el contador",
                alPulsar = { irA(Ruta.Facturas) },
            )
            FilaMenu(
                icono = Iconos.reporte,
                titulo = "Reportes",
                subtitulo = "Valorización, mermas y discrepancias",
                alPulsar = { irA(Ruta.Reportes) },
            )

            Separador()
            Seccion("Cuenta")
            FilaMenu(
                icono = Iconos.ajustes,
                titulo = "Ajustes e integraciones",
                subtitulo = "Claves de API y webhooks",
                alPulsar = { irA(Ruta.Ajustes) },
            )
            FilaMenu(
                icono = Iconos.cerrarSesion,
                titulo = "Cerrar sesión",
                subtitulo = null,
                alPulsar = alCerrarSesion,
                color = MaterialTheme.colorScheme.error,
                fondoIcono = Estado.agotadoContenedor,
                conAvance = false,
            )
        }
    }
}

@Composable
private fun CabeceraNegocio(nombre: String, moneda: String) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(Dimensiones.espacio),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier.padding(Dimensiones.espacioAmplio),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Dimensiones.miniatura)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(Dimensiones.radioGrande),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    nombre.iniciales(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
                Text(
                    nombre,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    moneda,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
}

/** «Papelería La Esquina» → «PE». Con una sola palabra, sus dos primeras letras. */
private fun String.iniciales(): String {
    val palabras = trim().split(" ").filter { it.isNotBlank() }
    return when {
        palabras.isEmpty() -> "?"
        palabras.size == 1 -> palabras[0].take(2).uppercase()
        else -> (palabras[0].take(1) + palabras[1].take(1)).uppercase()
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(
                start = Dimensiones.espacio,
                end = Dimensiones.espacio,
                top = Dimensiones.espacioMedio,
                bottom = Dimensiones.espacioCompacto,
            )
            .semantics { heading() },
    )
}

@Composable
private fun Separador() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioCompacto),
    )
}

@Composable
private fun FilaMenu(
    icono: ImageVector,
    titulo: String,
    subtitulo: String?,
    alPulsar: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fondoIcono: Color = MaterialTheme.colorScheme.secondaryContainer,
    conAvance: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
            .clickable(onClick = alPulsar)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Dimensiones.miniatura)
                .background(fondoIcono, RoundedCornerShape(Dimensiones.radio)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icono,
                contentDescription = null,
                tint = if (conAvance) MaterialTheme.colorScheme.primary else color,
                modifier = Modifier.size(Dimensiones.icono),
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = color)
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (conAvance) {
            Icon(
                Iconos.avanzar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Dimensiones.icono),
            )
        }
    }
}
