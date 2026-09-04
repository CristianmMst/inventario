package co.inventario.feature.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia

/**
 * T-080: entre el escaneo y la ficha. Código desconocido → alta precargada, con decisión
 * explícita: no se crea nada solo (RF-CAT-009 / RN-14).
 *
 * Era un `CircularProgressIndicator` desnudo bajo un título. Ahora se ve el código que se leyó
 * —que es lo que la persona quiere confirmar— y la espera dice qué está pasando.
 */
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

    PantallaInventario(titulo = "Código leído", alVolver = alVolver) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(horizontal = Dimensiones.espacioAmplio),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            Text(
                codigo,
                style = Tipografia.numeroFila,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimensiones.espacio),
            )

            val desconocido = estado.codigoDesconocido
            val error = estado.error
            when {
                estado.cargando -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Buscando el producto…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                desconocido != null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
                ) {
                    Box(
                        Modifier.padding(top = Dimensiones.espacioAmplio),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Iconos.escanear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(Dimensiones.iconoIlustracion),
                        )
                    }
                    Text("Este código no está en el catálogo", style = MaterialTheme.typography.titleLarge)
                    Text(
                        estado.mensaje.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    BotonPrincipal(
                        "Crear producto con este código",
                        { alCrearConCodigo(desconocido) },
                        icono = Iconos.anadir,
                    )
                    BotonSecundario("Volver a escanear", alVolver, icono = Iconos.escanear)
                }

                error != null -> EstadoError(
                    texto = error.mensaje,
                    alReintentar = { vm.resolver(codigo) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
