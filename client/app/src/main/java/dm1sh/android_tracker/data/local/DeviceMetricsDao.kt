package dm1sh.android_tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceMetricsDao {

    @Insert
    suspend fun insert(metric: DeviceMetricsEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(metrics: List<DeviceMetricsEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM device_metrics")
    suspend fun count(): Int

    @Query("SELECT * FROM device_metrics WHERE synced = 0 ORDER BY capturedAt ASC")
    suspend fun getUnsynced(): List<DeviceMetricsEntity>

    @Query("SELECT * FROM device_metrics WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DeviceMetricsEntity>

    @Update
    suspend fun updateAll(metrics: List<DeviceMetricsEntity>)

    @Query("UPDATE device_metrics SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM device_metrics WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
