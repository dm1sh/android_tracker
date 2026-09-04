package dm1sh.android_tracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<UsageEventEntity>): List<Long>

    @Insert
    suspend fun insert(event: UsageEventEntity): Long

    @Query("SELECT COALESCE(MAX(timestamp), -1) FROM usage_events")
    suspend fun getLastEventTimestamp(): Long

    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun count(): Int

    @Query("SELECT * FROM usage_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<UsageEventEntity>

    @Query("SELECT * FROM usage_events WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<UsageEventEntity>

    @Update
    suspend fun updateAll(events: List<UsageEventEntity>)

    @Query("UPDATE usage_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM usage_events WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
