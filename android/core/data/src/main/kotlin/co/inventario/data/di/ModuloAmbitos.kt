package co.inventario.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Ámbito que sobrevive a la pantalla: subidas de foto que no deben bloquear la navegación (RNF-05). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AmbitoAplicacion

@Module
@InstallIn(SingletonComponent::class)
object ModuloAmbitos {
    @Provides
    @Singleton
    @AmbitoAplicacion
    fun ambitoAplicacion(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
