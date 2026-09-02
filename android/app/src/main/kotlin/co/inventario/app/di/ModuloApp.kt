package co.inventario.app.di

import co.inventario.app.BuildConfig
import co.inventario.data.red.ConfiguracionRed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloApp {

    /** La URL del backend la fija la variante de compilación (plan.md §10). */
    @Provides
    @Singleton
    fun configuracionRed(): ConfiguracionRed =
        ConfiguracionRed(baseUrl = BuildConfig.BACKEND_URL, registrarTrafico = BuildConfig.DEBUG)
}
