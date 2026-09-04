package co.inventario.feature.escaneo

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.AvisoSobreCamara
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.LocalPendientesDeEnvio
import co.inventario.designsystem.componentes.VisorEscaneo
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Pantalla de escaneo (T-078/T-079). Es la pantalla de inicio: la cámara arranca al entrar y se
 * libera al salir. `alLeerCodigo` recibe tanto lo escaneado como lo tecleado: para el resto de
 * la app no hay diferencia (RNF-15).
 *
 * Antes la cámara era un `PreviewView` desnudo y al pie había cuatro botones iguales. Ahora hay
 * visor, la lectura se acusa con un destello y una vibración —de pie y con ruido, la pantalla
 * sola no basta— y buscar o ir al menú vive en la barra inferior, no compitiendo aquí.
 */
@Composable
fun PantallaEscaneo(
    alLeerCodigo: (String) -> Unit,
    barraInferior: @Composable () -> Unit = {},
    vm: EscaneoViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val haptica = LocalHapticFeedback.current
    val pendientes = LocalPendientesDeEnvio.current
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::permisoResuelto)

    var destello by remember { mutableStateOf(false) }
    val brilloDestello by animateFloatAsState(if (destello) 0.85f else 0f, label = "destello")

    LaunchedEffect(destello) {
        if (destello) {
            runCatching { haptica.performHapticFeedback(HapticFeedbackType.LongPress) }
            delay(160)
            destello = false
        }
    }

    LaunchedEffect(Unit) {
        val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (concedido) vm.permisoResuelto(true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                AreaTeclado(
                    tecleando = estado.tecleando,
                    codigo = estado.codigoTecleado,
                    error = estado.errorCodigo,
                    alCambiarCodigo = vm::cambiarCodigo,
                    alConfirmar = { vm.codigoConfirmado()?.let(alLeerCodigo) },
                    alAlternar = { vm.teclear(!estado.tecleando) },
                )
                barraInferior()
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when (estado.permiso) {
                EstadoPermiso.CONCEDIDO -> {
                    VisorEscaneo(
                        linternaEncendida = estado.linterna,
                        alAlternarLinterna = vm::alternarLinterna,
                        aviso = if (pendientes > 0) {
                            {
                                AvisoSobreCamara(
                                    if (pendientes == 1) "1 movimiento por enviar" else "$pendientes movimientos por enviar",
                                    Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                                )
                            }
                        } else {
                            null
                        },
                    ) {
                        VistaCamara(
                            linterna = estado.linterna,
                            alLeer = { codigo ->
                                destello = true
                                alLeerCodigo(codigo)
                            },
                        )
                    }
                    if (brilloDestello > 0f) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = brilloDestello)))
                    }
                }

                EstadoPermiso.SIN_PEDIR -> ExplicacionPermiso(
                    alPermitir = { lanzador.launch(Manifest.permission.CAMERA) },
                )

                EstadoPermiso.DENEGADO -> SinCamara(
                    alPedirDeNuevo = { lanzador.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

/**
 * El teclado manual, que es el camino completo cuando no hay cámara (RNF-15). Cerrado ocupa una
 * sola línea para no robarle sitio al visor.
 */
@Composable
private fun AreaTeclado(
    tecleando: Boolean,
    codigo: String,
    error: String?,
    alCambiarCodigo: (String) -> Unit,
    alConfirmar: () -> Unit,
    alAlternar: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
    ) {
        if (tecleando) {
            CampoTexto(
                valor = codigo,
                alCambiar = alCambiarCodigo,
                etiqueta = "Código de barras",
                error = error,
                tipoTeclado = KeyboardType.Number,
            )
            BotonPrincipal("Buscar este código", alConfirmar)
            BotonSecundario("Ocultar el teclado", alAlternar)
        } else {
            BotonSecundario("Teclear el código", alAlternar, icono = Iconos.teclado)
        }
    }
}

@Composable
private fun ExplicacionPermiso(alPermitir: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(Dimensiones.espacioAmplio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Para leer códigos hace falta la cámara", style = MaterialTheme.typography.headlineMedium)
        Text(
            "La app usa la cámara solo mientras esta pantalla está abierta, para leer el código de barras " +
                "del producto. No guarda fotos ni video. Si prefieres, puedes teclear el código.",
            style = MaterialTheme.typography.bodyLarge,
        )
        BotonPrincipal("Permitir la cámara", alPermitir, icono = Iconos.camara)
    }
}

@Composable
private fun SinCamara(alPedirDeNuevo: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(Dimensiones.espacioAmplio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Sin cámara también funciona", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No hay permiso para usar la cámara. Escribe el código que está debajo de las barras; " +
                "todo lo demás funciona igual.",
            style = MaterialTheme.typography.bodyLarge,
        )
        BotonSecundario("Volver a pedir permiso", alPedirDeNuevo, icono = Iconos.camara)
    }
}

/** Vista previa de CameraX con el analizador de ML Kit. Se desmonta con la pantalla. */
@Composable
private fun VistaCamara(linterna: Boolean, alLeer: (String) -> Unit) {
    val contexto = LocalContext.current
    val duenoCiclo = LocalLifecycleOwner.current
    val alLeerActual by rememberUpdatedState(alLeer)
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val analizador = remember { AnalizadorCodigos { codigo -> alLeerActual(codigo) } }
    var camara by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(linterna, camara) {
        camara?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(linterna)
    }

    DisposableEffect(Unit) {
        onDispose {
            // RNF-10: al salir se libera la cámara y el modelo.
            runCatching { ProcessCameraProvider.getInstance(contexto).get().unbindAll() }
            analizador.cerrar()
            ejecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { vista ->
                vista.scaleType = PreviewView.ScaleType.FILL_CENTER
                val futuro = ProcessCameraProvider.getInstance(ctx)
                futuro.addListener({
                    val proveedor = futuro.get()
                    val vistaPrevia = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                    val analisis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(ejecutor, analizador) }
                    proveedor.unbindAll()
                    camara = runCatching {
                        proveedor.bindToLifecycle(duenoCiclo, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analisis)
                    }.getOrNull()
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}
