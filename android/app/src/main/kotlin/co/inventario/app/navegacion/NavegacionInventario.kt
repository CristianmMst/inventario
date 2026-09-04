package co.inventario.app.navegacion

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.inventario.designsystem.componentes.BarraNavegacion
import co.inventario.designsystem.componentes.Destino
import co.inventario.designsystem.componentes.LocalPendientesDeEnvio
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.feature.ajustes.PantallaAjustes
import co.inventario.feature.auth.PantallaLogin
import co.inventario.feature.auth.PantallaRegistro
import co.inventario.feature.catalogo.AccionesFicha
import co.inventario.feature.catalogo.PantallaAltaProducto
import co.inventario.feature.catalogo.PantallaBusqueda
import co.inventario.feature.catalogo.PantallaFicha
import co.inventario.feature.catalogo.PantallaResolverCodigo
import co.inventario.feature.compras.PantallaNuevaOrden
import co.inventario.feature.compras.PantallaOrdenDetalle
import co.inventario.feature.compras.PantallaOrdenes
import co.inventario.feature.compras.PantallaProveedores
import co.inventario.feature.compras.PantallaRecepcion
import co.inventario.feature.escaneo.PantallaEscaneo
import co.inventario.feature.facturas.PantallaFacturas
import co.inventario.feature.facturas.PantallaNuevaFactura
import co.inventario.feature.movimientos.PantallaConteo
import co.inventario.feature.movimientos.PantallaHistorial
import co.inventario.feature.movimientos.PantallaMovimiento
import co.inventario.feature.reportes.PantallaReponer
import co.inventario.feature.reportes.PantallaReportes
import kotlinx.coroutines.flow.Flow

/**
 * Los cuatro destinos de primer nivel. El escaneo sigue siendo el de arranque (HU-03: «la
 * cámara es la puerta de todo»), pero deja de ser el **único** camino: antes todo colgaba de él
 * y de un menú con siete botones iguales.
 *
 * «Reponer» está aquí, y no «Movimientos», por una razón dura: la API no tiene un
 * `GET /movimientos` global, solo por producto, y añadir uno rompería el contrato de paridad de
 * RF-INT-008. Se sirve de los reportes que ya existen y además cubre un momento de uso literal
 * de la spec (§1.4, «mirando estantes vacíos»).
 */
private val destinosRaiz: List<Pair<Ruta, Destino>> = listOf(
    Ruta.Inicio to Destino("Escanear", Iconos.escanear, Iconos.escanearActivo),
    Ruta.Busqueda to Destino("Catálogo", Iconos.catalogo, Iconos.catalogoActivo),
    Ruta.Reponer to Destino("Reponer", Iconos.reponer, Iconos.reponerActivo),
    Ruta.Menu to Destino("Más", Iconos.mas, Iconos.masActivo),
)

/**
 * NavHost de la app (T-074). `sesionCerrada` viene de la capa de red: al cerrarse la sesión se
 * vuelve al inicio de sesión desde cualquier pantalla (RF-AUT-003). `monedaBase` y
 * `nombreNegocio` son los del negocio.
 */
@Composable
fun NavegacionInventario(
    haySesion: Boolean,
    monedaBase: () -> String,
    nombreNegocio: () -> String,
    sesionCerrada: Flow<Unit>,
) {
    val nav = rememberNavController()
    val pendientesVm: PendientesViewModel = hiltViewModel()
    val pendientes by pendientesVm.pendientes.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        sesionCerrada.collect { nav.aLogin() }
    }

    val entrada by nav.currentBackStackEntryAsState()
    val indiceActivo = destinosRaiz.indexOfFirst { (ruta, _) ->
        entrada?.destination?.hierarchy?.any { it.hasRoute(ruta::class) } == true
    }

    // La barra solo existe en los cuatro destinos de primer nivel; las pantallas de detalle
    // (ficha, movimiento, recepción…) se abren encima, con flecha atrás y sin barra.
    val barraInferior: @Composable () -> Unit = {
        if (indiceActivo >= 0) {
            BarraNavegacion(
                destinos = destinosRaiz.map { it.second },
                indiceActivo = indiceActivo,
                alElegir = { indice -> nav.aRaiz(destinosRaiz[indice].first) },
            )
        }
    }

    CompositionLocalProvider(LocalPendientesDeEnvio provides pendientes) {
        // Sin tamaño explícito el NavHost mide 0×0 dentro del ComposeView (wrap_content) y no se ve nada.
        NavHost(
            navController = nav,
            startDestination = if (haySesion) Ruta.Inicio else Ruta.Login,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Ruta.Login> {
                PantallaLogin(
                    alIniciarSesion = { nav.navigate(Ruta.Inicio) { popUpTo(0) { inclusive = true } } },
                    irARegistro = { nav.navigate(Ruta.Registro) },
                )
            }
            composable<Ruta.Registro> {
                PantallaRegistro(
                    alRegistrarse = { nav.navigate(Ruta.Inicio) { popUpTo(0) { inclusive = true } } },
                    irALogin = { nav.popBackStack() },
                )
            }

            // --- Destinos de primer nivel ------------------------------------------------------
            composable<Ruta.Inicio> {
                PantallaEscaneo(
                    alLeerCodigo = { codigo -> nav.navigate(Ruta.ResolverCodigo(codigo)) },
                    barraInferior = barraInferior,
                )
            }
            composable<Ruta.Busqueda> {
                PantallaBusqueda(
                    alAbrirFicha = { id -> nav.navigate(Ruta.Producto(id)) },
                    alCrearProducto = { nav.navigate(Ruta.AltaProducto()) },
                    barraInferior = barraInferior,
                )
            }
            composable<Ruta.Reponer> {
                PantallaReponer(
                    alAbrirProducto = { id -> nav.navigate(Ruta.Producto(id)) },
                    alRegistrarEntrada = { id -> nav.navigate(Ruta.Movimiento(id, "entrada")) },
                    barraInferior = barraInferior,
                )
            }
            composable<Ruta.Menu> {
                val sesion: SesionViewModel = hiltViewModel()
                PantallaMenu(
                    nombreNegocio = nombreNegocio(),
                    moneda = monedaBase(),
                    irA = { nav.navigate(it) },
                    alCerrarSesion = { sesion.cerrarSesion { nav.aLogin() } },
                    barraInferior = barraInferior,
                )
            }

            // --- Catálogo y movimientos --------------------------------------------------------
            composable<Ruta.ResolverCodigo> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.ResolverCodigo>()
                PantallaResolverCodigo(
                    codigo = ruta.codigo,
                    // La resolución sale de la pila: "atrás" desde la ficha vuelve al escaneo.
                    alAbrirFicha = { id -> nav.navigate(Ruta.Producto(id)) { popUpTo<Ruta.Inicio>() } },
                    alCrearConCodigo = { codigo -> nav.navigate(Ruta.AltaProducto(codigo)) { popUpTo<Ruta.Inicio>() } },
                    alVolver = nav::volverAlInicio,
                )
            }
            composable<Ruta.Producto> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Producto>()
                PantallaFicha(
                    productoId = ruta.productoId,
                    acciones = AccionesFicha(
                        registrarSalida = { id -> nav.navigate(Ruta.Movimiento(id, "salida")) },
                        registrarEntrada = { id -> nav.navigate(Ruta.Movimiento(id, "entrada")) },
                        registrarMerma = { id -> nav.navigate(Ruta.Movimiento(id, "merma")) },
                        contar = { id -> nav.navigate(Ruta.Conteo(id)) },
                        verHistorial = { id -> nav.navigate(Ruta.Historial(id)) },
                        editar = { id -> nav.navigate(Ruta.EditarProducto(id)) },
                        volverAEscanear = nav::volverAlInicio,
                    ),
                    alVolver = { nav.popBackStack() },
                )
            }
            composable<Ruta.AltaProducto> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.AltaProducto>()
                PantallaAltaProducto(
                    codigoBarras = ruta.codigoBarras,
                    productoId = null,
                    monedaBase = monedaBase(),
                    alGuardar = { id -> nav.navigate(Ruta.Producto(id)) { popUpTo<Ruta.Inicio>() } },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.EditarProducto> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.EditarProducto>()
                PantallaAltaProducto(
                    codigoBarras = null,
                    productoId = ruta.productoId,
                    monedaBase = monedaBase(),
                    alGuardar = { nav.popBackStack() },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Movimiento> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Movimiento>()
                PantallaMovimiento(
                    productoId = ruta.productoId,
                    tipo = TipoMovimiento.desde(ruta.tipo),
                    // Tras registrar se vuelve a la ficha, que recarga el stock desde el servidor.
                    alRegistrar = { nav.popBackStack() },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Conteo> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Conteo>()
                PantallaConteo(
                    productoId = ruta.productoId,
                    alRegistrar = { nav.popBackStack() },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Historial> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Historial>()
                PantallaHistorial(productoId = ruta.productoId, alVolver = { nav.popBackStack() })
            }

            // --- H9: compras, documentos y ajustes ---------------------------------------------
            composable<Ruta.Proveedores> { PantallaProveedores(alVolver = { nav.popBackStack() }) }
            composable<Ruta.Ordenes> {
                PantallaOrdenes(
                    alAbrir = { id -> nav.navigate(Ruta.Orden(id)) },
                    alNueva = { nav.navigate(Ruta.NuevaOrden) },
                    alVolver = { nav.popBackStack() },
                )
            }
            composable<Ruta.Orden> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Orden>()
                PantallaOrdenDetalle(
                    ordenId = ruta.ordenId,
                    alRecibir = { id -> nav.navigate(Ruta.Recepcion(id)) },
                    alVolver = { nav.popBackStack() },
                )
            }
            composable<Ruta.NuevaOrden> {
                PantallaNuevaOrden(
                    monedaBase = monedaBase(),
                    alCrear = { id -> nav.navigate(Ruta.Orden(id)) { popUpTo<Ruta.Ordenes>() } },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Recepcion> { entradaRuta ->
                val ruta = entradaRuta.toRoute<Ruta.Recepcion>()
                PantallaRecepcion(
                    ordenId = ruta.ordenId,
                    monedaBase = monedaBase(),
                    // Confirmada: se vuelve a donde se venía (menú u orden), que recarga.
                    alConfirmar = { nav.popBackStack() },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Facturas> {
                PantallaFacturas(alNueva = { nav.navigate(Ruta.NuevaFactura) }, alVolver = { nav.popBackStack() })
            }
            composable<Ruta.NuevaFactura> {
                PantallaNuevaFactura(
                    monedaBase = monedaBase(),
                    alRegistrar = { nav.popBackStack() },
                    alCancelar = { nav.popBackStack() },
                )
            }
            composable<Ruta.Reportes> {
                PantallaReportes(
                    alAbrirProducto = { id -> nav.navigate(Ruta.Producto(id)) },
                    alVolver = { nav.popBackStack() },
                )
            }
            composable<Ruta.Ajustes> {
                val sesion: SesionViewModel = hiltViewModel()
                PantallaAjustes(
                    alCerrarSesion = { sesion.cerrarSesion { nav.aLogin() } },
                    alVolver = { nav.popBackStack() },
                )
            }
        }
    }
}

/**
 * Cambiar de destino de primer nivel no apila: vuelve al inicio del grafo y restaura el estado
 * que tuviera ese destino. Sin esto, cuatro toques en la barra dejan cuatro pantallas en la
 * pila y el botón atrás las recorre hacia atrás una a una.
 */
private fun NavHostController.aRaiz(ruta: Ruta) {
    navigate(ruta) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.volverAlInicio() {
    popBackStack(Ruta.Inicio, inclusive = false)
}

private fun NavHostController.aLogin() {
    navigate(Ruta.Login) { popUpTo(0) { inclusive = true } }
}
