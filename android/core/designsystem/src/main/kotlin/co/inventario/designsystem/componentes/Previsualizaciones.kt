package co.inventario.designsystem.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.InventarioTema

/**
 * Previsualizaciones del sistema de diseño. No había ninguna en toda la app pese a tener
 * `ui-tooling-preview` cableado: cada cambio de un componente compartido obligaba a compilar,
 * instalar y navegar hasta la pantalla que lo usaba.
 *
 * Sirven además de inventario visual: lo que no aparece aquí, no existe como componente.
 */
@Composable
private fun Lienzo(contenido: @Composable () -> Unit) {
    InventarioTema {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(Dimensiones.espacio),
                verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
            ) {
                contenido()
            }
        }
    }
}

@Preview(name = "Botones", showBackground = true, widthDp = 390)
@Composable
private fun VistaBotones() = Lienzo {
    BotonPrincipal("Registrar salida", {}, icono = Iconos.salida)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        BotonSecundario("Entrada", {}, Modifier.weight(1f), icono = Iconos.entrada)
        BotonSecundario("Merma", {}, Modifier.weight(1f), icono = Iconos.merma, color = Estado.bajoMinimo)
    }
    BotonPrincipal("Guardando…", {}, habilitado = false)
    BotonTexto("Anular", {}, color = MaterialTheme.colorScheme.error)
}

@Preview(name = "Campos y cantidad", showBackground = true, widthDp = 390)
@Composable
private fun VistaCampos() = Lienzo {
    CampoTexto("Resma carta 75 g", {}, "Nombre")
    CampoTexto("", {}, "Nota (obligatoria)", apoyo = "Dónde y cómo contaste.")
    CampoTexto("abc", {}, "Correo", error = "Ese correo no tiene forma de correo.")
    SelectorCantidad("2", {}, "Cantidad")
}

@Preview(name = "Estado del stock", showBackground = true, widthDp = 390)
@Composable
private fun VistaEstadoStock() = Lienzo {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        PildoraEstado(EstadoStock.EN_RANGO)
        PildoraEstado(EstadoStock.BAJO_MINIMO)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        PildoraEstado(EstadoStock.AGOTADO)
        PildoraEstado(EstadoStock.ARCHIVADO)
    }
    TarjetaStock("38", "unidades", EstadoStock.EN_RANGO, minimo = "20", proporcionDelMinimo = 0.95f)
    TarjetaStock("4", "unidades", EstadoStock.BAJO_MINIMO, minimo = "20", proporcionDelMinimo = 0.1f)
    TarjetaStock("0", "unidades", EstadoStock.AGOTADO)
}

@Preview(name = "Filas de listado", showBackground = true, widthDp = 390)
@Composable
private fun VistaFilas() = Lienzo {
    FilaProducto("Resma carta 75 g", "SKU-0142 · Papelería", "38", "unidades", EstadoStock.EN_RANGO, {})
    FilaProducto("Resma oficio 75 g", "SKU-0143 · Papelería", "4", "unidades", EstadoStock.BAJO_MINIMO, {})
    FilaProducto("Resma carta 90 g", "SKU-0148 · Papelería", "0", "unidades", EstadoStock.AGOTADO, {})
    FilaMovimiento(SentidoMovimiento.SALIDA, "Salida", "venta · hoy 14:05", "−2", "36")
    FilaMovimiento(SentidoMovimiento.ENTRADA, "Entrada", "recepción de compra · ayer", "+24", "38")
    FilaMovimiento(SentidoMovimiento.MERMA, "Merma", "rotura · 28/08", "−1", "37", anulado = true)
}

@Preview(name = "Tarjeta de datos", showBackground = true, widthDp = 390)
@Composable
private fun VistaTarjetaDatos() = Lienzo {
    TarjetaDatos {
        FilaDato("Precio de venta", Formato.monto("14900.0000", "COP"))
        FilaDato("Costo actual", Formato.monto("9450.0000", "COP"))
        FilaDato("Unidad de medida", "Unidad", ultima = true)
    }
}

@Preview(name = "Chips", showBackground = true, widthDp = 390)
@Composable
private fun VistaChips() = Lienzo {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        ChipFiltro("Bajo mínimo", true, {})
        ChipFiltro("Agotados", false, {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        PildoraEstadoOrden("Emitida")
        PildoraEstadoOrden(
            "Pagada",
            contenedor = Estado.enRangoContenedor,
            sobreContenedor = Estado.sobreEnRangoContenedor,
        )
        PildoraEstadoOrden(
            "Pendiente",
            contenedor = Estado.bajoMinimoContenedor,
            sobreContenedor = Estado.sobreBajoMinimoContenedor,
        )
    }
}

@Preview(name = "Vacío", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun VistaVacio() = Lienzo {
    EstadoVacio(
        titulo = "Nada bajo mínimo",
        explicacion = "Todo el catálogo está por encima de su mínimo. Nada que pedir hoy.",
        textoAccion = "Ver el catálogo",
        alAccionar = {},
    )
}

@Preview(name = "Error", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun VistaError() = Lienzo {
    EstadoError("No se guardó. Sin conexión: reintentando…", {})
    MensajeError("Solo hay 38 en stock.")
}

@Preview(name = "Cargando", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun VistaCargando() = Lienzo {
    EsqueletoLista(filas = 4)
}

@Preview(name = "Aviso de envíos pendientes", showBackground = true, widthDp = 390)
@Composable
private fun VistaBanner() = InventarioTema {
    Column {
        BannerConexion(1)
        BannerConexion(3)
    }
}

@Preview(name = "Barra inferior", showBackground = true, widthDp = 390)
@Composable
private fun VistaBarraNavegacion() = InventarioTema {
    BarraNavegacion(
        destinos = listOf(
            Destino("Escanear", Iconos.escanear, Iconos.escanearActivo),
            Destino("Catálogo", Iconos.catalogo, Iconos.catalogoActivo),
            Destino("Reponer", Iconos.reponer, Iconos.reponerActivo, insignia = 7),
            Destino("Más", Iconos.mas, Iconos.masActivo),
        ),
        indiceActivo = 0,
        alElegir = {},
    )
}

@Preview(name = "Tipografía", showBackground = true, widthDp = 390)
@Composable
private fun VistaTipografia() = Lienzo {
    Text("Reponer", style = MaterialTheme.typography.headlineLarge)
    Text("Registrar salida", style = MaterialTheme.typography.headlineMedium)
    Text("Resma carta 75 g", style = MaterialTheme.typography.titleLarge)
    Text("Cuaderno cosido 100 hojas", style = MaterialTheme.typography.titleMedium)
    Text("Escribe el código que está debajo de las barras.", style = MaterialTheme.typography.bodyLarge)
    Text("SKU-0142 · Papelería · mínimo 20", style = MaterialTheme.typography.bodyMedium)
    StockDestacado("38", "unidades")
}
