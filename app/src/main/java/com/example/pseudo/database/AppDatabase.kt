package com.example.pseudo.database

import android.content.Context
import androidx.room.*
import com.example.pseudo.models.ImageRecord
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "processed_images")
data class ImageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val outputPath: String,
    val originalFilename: String,
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val originalHash: String = "",
    val processedHash: String = "",
    val mode: String = "standard"
)

@Dao
interface ImageDao {
    @Query("SELECT * FROM processed_images ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ImageRecord>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ImageRecord): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ImageRecord>)
    
    @Query("DELETE FROM processed_images WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM processed_images")
    suspend fun deleteAll()
}

@Database(entities = [ImageRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase @InjectConstructor constructor(
    context: Context
) : RoomDatabase() {
    abstract fun imageDao(): ImageDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pseudo_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
