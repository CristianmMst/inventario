package co.inventario.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

/**
 * Fila de la bandeja de salida (plan.md §8.5). La escritura va serializada en JSON: Room solo
 * necesita consultar por estado y ordenar por fecha; el contenido lo interpreta la capa outbox.
 */
@Entity(tableName = "operaciones_pendientes")
data class OperacionPendienteEntidad(
    @PrimaryKey val clave: String,
    val escritura: String,
    val estado: String,
    @ColumnInfo(name = "creada_en") val creadaEn: Long,
    val intentos: Int,
    @ColumnInfo(name = "ultimo_error") val ultimoError: String?,
    val respuesta: String?,
)

@Dao
interface BandejaSalidaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(operacion: OperacionPendienteEntidad)

    @Update
    suspend fun actualizar(operacion: OperacionPendienteEntidad)

    @Query("SELECT * FROM operaciones_pendientes WHERE clave = :clave")
    suspend fun obtener(clave: String): OperacionPendienteEntidad?

    @Query("SELECT * FROM operaciones_pendientes WHERE estado = 'PENDIENTE' ORDER BY creada_en ASC")
    suspend fun pendientes(): List<OperacionPendienteEntidad>

    /** Las confirmadas y rechazadas viejas no aportan nada: se limpian al arrancar. */
    @Query("DELETE FROM operaciones_pendientes WHERE estado != 'PENDIENTE' AND creada_en < :antesDe")
    suspend fun purgarCerradas(antesDe: Long)
}

@Database(entities = [OperacionPendienteEntidad::class], version = 1, exportSchema = true)
abstract class BaseDatosInventario : RoomDatabase() {
    abstract fun bandejaSalida(): BandejaSalidaDao
}
