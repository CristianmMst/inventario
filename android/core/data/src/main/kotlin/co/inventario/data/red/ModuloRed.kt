package co.inventario.data.red

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** La app decide la URL del backend (BuildConfig por variante, plan.md §10). */
data class ConfiguracionRed(val baseUrl: String, val registrarTrafico: Boolean = false)

/** Canal por el que la capa de red avisa a la UI de que la sesión se cerró (RF-AUT-003). */
@Singleton
class SesionEventos @javax.inject.Inject constructor() : AvisoSesionCerrada {
    private val _cerradas = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cerradas: SharedFlow<Unit> = _cerradas.asSharedFlow()
    override fun sesionCerrada() {
        _cerradas.tryEmit(Unit)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloRed {

    @Provides
    @Singleton
    fun almacenSesion(@ApplicationContext contexto: Context): AlmacenSesion = SesionPreferencias(contexto)

    @Provides
    @Singleton
    fun avisoSesion(eventos: SesionEventos): AvisoSesionCerrada = eventos

    @Provides
    @Singleton
    fun okHttp(
        configuracion: ConfiguracionRed,
        almacen: AlmacenSesion,
        aviso: AvisoSesionCerrada,
    ): OkHttpClient {
        val base = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Una conexión keep-alive que el servidor ya cerró se detecta al escribir ("unexpected end
            // of stream"); OkHttp la reabre y reenvía. Es seguro para las escrituras porque todas
            // llevan Idempotency-Key (RNF-06): el reintento de negocio lo gobierna la bandeja de salida.
            .retryOnConnectionFailure(true)
        if (configuracion.registrarTrafico) {
            base.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return base
            .addInterceptor(InterceptorAutenticacion(almacen))
            .authenticator(RenovadorSesion(almacen, configuracion.baseUrl, aviso, base.build()))
            .build()
    }

    @Provides
    @Singleton
    fun retrofit(configuracion: ConfiguracionRed, cliente: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(configuracion.baseUrl)
            .client(cliente)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): InventarioApi = retrofit.create(InventarioApi::class.java)
}
