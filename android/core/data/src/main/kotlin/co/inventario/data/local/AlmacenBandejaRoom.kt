package co.inventario.data.local

import android.content.Context
import androidx.room.Room
import co.inventario.data.outbox.AlmacenBandeja
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.outbox.Escritura
import co.inventario.data.outbox.EstadoOperacion
import co.inventario.data.outbox.OperacionPendiente
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Adaptador Room del almacén de la bandeja: traduce entre la fila y el modelo de outbox. */
class AlmacenBandejaRoom @Inject constructor(private val dao: BandejaSalidaDao) : AlmacenBandeja {

    override suspend fun guardar(operacion: OperacionPendiente) = dao.insertar(operacion.aEntidad())

    override suspend fun actualizar(operacion: OperacionPendiente) = dao.actualizar(operacion.aEntidad())

    override suspend fun obtener(clave: String): OperacionPendiente? = dao.obtener(clave)?.aModelo()

    override suspend fun pendientes(): List<OperacionPendiente> = dao.pendientes().map { it.aModelo() }

    private fun OperacionPendiente.aEntidad() = OperacionPendienteEntidad(
        clave = clave,
        escritura = Json.encodeToString(Escritura.serializer(), escritura),
        estado = estado.name,
        creadaEn = creadaEn,
        intentos = intentos,
        ultimoError = ultimoError,
        respuesta = respuesta,
    )

    private fun OperacionPendienteEntidad.aModelo() = OperacionPendiente(
        clave = clave,
        escritura = Json.decodeFromString(Escritura.serializer(), escritura),
        estado = EstadoOperacion.valueOf(estado),
        creadaEn = creadaEn,
        intentos = intentos,
        ultimoError = ultimoError,
        respuesta = respuesta,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloLocal {

    @Provides
    @Singleton
    fun baseDatos(@ApplicationContext contexto: Context): BaseDatosInventario =
        Room.databaseBuilder(contexto, BaseDatosInventario::class.java, "inventario.db").build()

    @Provides
    fun bandejaDao(base: BaseDatosInventario): BandejaSalidaDao = base.bandejaSalida()

    @Provides
    @Singleton
    fun almacenBandeja(impl: AlmacenBandejaRoom): AlmacenBandeja = impl

    @Provides
    @Singleton
    fun bandejaSalida(almacen: AlmacenBandeja, api: InventarioApi): BandejaSalida = BandejaSalida(almacen, api)
}
