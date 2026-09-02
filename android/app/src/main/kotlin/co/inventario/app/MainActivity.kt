package co.inventario.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.inventario.app.navegacion.NavegacionInventario
import co.inventario.data.red.AlmacenSesion
import co.inventario.data.red.SesionEventos
import co.inventario.designsystem.tema.InventarioTema
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var almacenSesion: AlmacenSesion
    @Inject lateinit var sesionEventos: SesionEventos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val haySesion = almacenSesion.tokens() != null
        setContent {
            InventarioTema {
                NavegacionInventario(haySesion = haySesion, sesionCerrada = sesionEventos.cerradas)
            }
        }
    }
}
