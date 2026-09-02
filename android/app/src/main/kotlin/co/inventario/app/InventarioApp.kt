package co.inventario.app

import android.app.Application
import android.util.Log
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.red.AlmacenSesion
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class InventarioApp : Application() {

    @Inject lateinit var bandeja: BandejaSalida
    @Inject lateinit var almacenSesion: AlmacenSesion

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // RNF-06 (T-077): lo que quedó confirmado y sin respuesta se reenvía al arrancar, con la
        // misma clave de idempotencia. Sin sesión no hay a quién enviarlo: se hará al entrar.
        if (almacenSesion.tokens() != null) {
            ambito.launch {
                val resultados = runCatching { bandeja.reintentarPendientes() }.getOrElse { emptyList() }
                if (resultados.isNotEmpty()) Log.i("Bandeja", "Reintentadas ${resultados.size} operaciones pendientes")
            }
        }
    }
}
