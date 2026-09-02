package co.inventario.data.repositorio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloRepositorios {
    @Binds
    abstract fun sesion(impl: RepositorioSesionApi): RepositorioSesion

    @Binds
    abstract fun catalogo(impl: RepositorioCatalogoApi): RepositorioCatalogo

    @Binds
    abstract fun movimientos(impl: RepositorioMovimientosApi): RepositorioMovimientos
}
