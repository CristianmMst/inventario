package co.inventario.feature.movimientos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.SelectorCantidad
import co.inventario.designsystem.componentes.TarjetaDatos
import co.inventario.designsystem.componentes.FilaDato
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import co.inventario.domain.modelo.Conteo
import co.inventario.domain.modelo.TipoUnidad

/**
 * T-086: conteo físico (RF-INV-013): la diferencia se ve antes de confirmar.
 *
 * Aquí la diferencia deja de ser una frase entre otras y pasa a ser el elemento dominante, con
 * su signo y su color: es lo que decide si vale la pena confirmar. La nota es obligatoria por
 * spec (RF-INV-010) y se dice desde el principio, no al fallar el envío.
 */
@Composable
fun PantallaConteo(
    productoId: String,
    alRegistrar: (Conteo) -> Unit,
    alCancelar: () -> Unit,
    vm: ConteoViewModel = hiltViewModel<ConteoViewModel, ConteoViewModel.Fabrica>(creationCallback = { it.crear(productoId) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is ConteoSuceso.Registrado) alRegistrar(it.conteo) } }

    PantallaInventario(
        titulo = "Conteo físico",
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                androidx.compose.foundation.layout.Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Guardando…" else "Confirmar el conteo",
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
            estado.producto?.let { producto ->
                Text(producto.nombre, style = MaterialTheme.typography.titleLarge)
                TarjetaDatos {
                    FilaDato("Según el sistema", "${estado.stockActual} ${producto.unidad.nombre}", ultima = true)
                }
            }

            SelectorCantidad(
                valor = estado.contada,
                alCambiar = vm::cambiarContada,
                unidad = "Cantidad contada",
                error = estado.erroresCampo["contada"],
                admiteDecimales = estado.producto?.unidad?.tipo != TipoUnidad.DISCRETA,
                habilitado = !estado.cargando,
            )

            estado.diferencia?.let { Diferencia(it) }

            CampoTexto(
                valor = estado.nota,
                alCambiar = vm::cambiarNota,
                etiqueta = "Nota (obligatoria)",
                error = estado.erroresCampo["nota"],
                apoyo = "Dónde y cómo contaste. Todo ajuste tiene que quedar explicado.",
            )

            estado.aviso?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MensajeError(estado.error)
        }
    }
}

/** La diferencia entre lo contado y lo que dice el sistema, que es la razón de esta pantalla. */
@Composable
private fun Diferencia(diferencia: String) {
    val coincide = diferencia == "0"
    val faltan = diferencia.startsWith("-")
    val color: Color = when {
        coincide -> Estado.enRango
        faltan -> Estado.agotado
        else -> Estado.bajoMinimo
    }
    val contenedor: Color = when {
        coincide -> Estado.enRangoContenedor
        faltan -> Estado.agotadoContenedor
        else -> Estado.bajoMinimoContenedor
    }
    val sobre: Color = when {
        coincide -> Estado.sobreEnRangoContenedor
        faltan -> Estado.sobreAgotadoContenedor
        else -> Estado.sobreBajoMinimoContenedor
    }
    val explicacion = when {
        coincide -> "Coincide con el sistema. No se registrará ningún ajuste."
        faltan -> "Se registrará un ajuste que resta."
        else -> "Se registrará un ajuste que suma."
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(contenedor, RoundedCornerShape(Dimensiones.radioGrande))
            .padding(Dimensiones.espacio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (coincide) Iconos.confirmar else Iconos.merma,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimensiones.iconoGrande),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
            Text(
                if (coincide) "Sin diferencia" else if (faltan) "Faltan ${diferencia.removePrefix("-")}" else "Sobran $diferencia",
                style = Tipografia.stockSecundario,
                color = sobre,
            )
            Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = sobre)
        }
    }
}
