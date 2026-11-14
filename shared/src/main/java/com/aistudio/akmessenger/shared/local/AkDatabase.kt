package com.aistudio.akmessenger.shared.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_records")
data class CachedRecordEntity(
    @PrimaryKey val key: String,
    val bucket: String,
    val jsonPayload: String,
    val updatedAt: Long
)

@Dao
interface CachedRecordDao {
    @Query("SELECT * FROM cached_records WHERE bucket = :bucket ORDER BY updatedAt DESC")
    fun observeBucket(bucket: String): Flow<List<CachedRecordEntity>>

    @Query("SELECT * FROM cached_records WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): CachedRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<CachedRecordEntity>)

    @Query("DELETE FROM cached_records WHERE bucket = :bucket")
    suspend fun clearBucket(bucket: String)

    @Query("DELETE FROM cached_records WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}

@Database(entities = [CachedRecordEntity::class], version = 1, exportSchema = false)
abstract class AkDatabase : RoomDatabase() {
    abstract fun cachedRecordDao(): CachedRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AkDatabase? = null

        fun getInstance(context: Context, dbName: String = "ak_messenger_cache.db"): AkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AkDatabase::class.java,
                    dbName
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
