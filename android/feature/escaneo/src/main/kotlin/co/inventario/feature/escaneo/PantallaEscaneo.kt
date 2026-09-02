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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.tema.Dimensiones
import java.util.concurrent.Executors

/**
 * Pantalla de escaneo (T-078/T-079). Es la pantalla de inicio: la cámara arranca al entrar y se
 * libera al salir. `alLeerCodigo` recibe tanto lo escaneado como lo tecleado: para el resto de
 * la app no hay diferencia (RNF-15).
 */
@Composable
fun PantallaEscaneo(
    alLeerCodigo: (String) -> Unit,
    irABuscar: () -> Unit,
    irAMenu: () -> Unit,
    vm: EscaneoViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::permisoResuelto)

    LaunchedEffect(Unit) {
        val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (concedido) vm.permisoResuelto(true)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (estado.permiso) {
                EstadoPermiso.CONCEDIDO -> VistaCamara(linterna = estado.linterna, alLeer = alLeerCodigo)
                EstadoPermiso.SIN_PEDIR -> ExplicacionPermiso(alPermitir = { lanzador.launch(Manifest.permission.CAMERA) })
                EstadoPermiso.DENEGADO -> Column(Modifier.padding(Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio)) {
                    Text("Sin cámara también funciona", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "No hay permiso para usar la cámara. Escribe el código que está debajo de las barras; " +
                            "todo lo demás funciona igual.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    BotonSecundario("Volver a pedir permiso", { lanzador.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        Column(Modifier.padding(Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            if (estado.tecleando) {
                CampoTexto(
                    valor = estado.codigoTecleado,
                    alCambiar = vm::cambiarCodigo,
                    etiqueta = "Código de barras",
                    error = estado.errorCodigo,
                    tipoTeclado = KeyboardType.Number,
                )
                BotonPrincipal("Buscar este código", { vm.codigoConfirmado()?.let(alLeerCodigo) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                if (estado.permiso == EstadoPermiso.CONCEDIDO) {
                    BotonSecundario(if (estado.linterna) "Apagar linterna" else "Linterna", vm::alternarLinterna, Modifier.weight(1f))
                }
                BotonSecundario(if (estado.tecleando) "Ocultar teclado" else "Teclear código", { vm.teclear(!estado.tecleando) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Buscar por nombre", irABuscar, Modifier.weight(1f))
                BotonSecundario("Compras y más", irAMenu, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExplicacionPermiso(alPermitir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Para leer códigos hace falta la cámara", style = MaterialTheme.typography.headlineMedium)
        Text(
            "La app usa la cámara solo mientras esta pantalla está abierta, para leer el código de barras " +
                "del producto. No guarda fotos ni video. Si prefieres, puedes teclear el código.",
            style = MaterialTheme.typography.bodyLarge,
        )
        BotonPrincipal("Permitir la cámara", alPermitir)
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
