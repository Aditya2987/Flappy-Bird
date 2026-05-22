package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "high_scores")
data class HighScore(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerName: String,
    val score: Int,
    val date: Long = System.currentTimeMillis(),
    val gameMode: String = "Normal" // "Easy", "Normal", "Hard"
)

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores ORDER BY score DESC, date DESC LIMIT 30")
    fun getTopScores(): Flow<List<HighScore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(highScore: HighScore)

    @Query("SELECT MAX(score) FROM high_scores")
    fun getPersonalBest(): Flow<Int?>

    @Query("SELECT MAX(score) FROM high_scores WHERE gameMode = :mode")
    fun getPersonalBestForMode(mode: String): Flow<Int?>

    @Query("DELETE FROM high_scores")
    suspend fun clearAllScores()
}

@Database(entities = [HighScore::class], version = 1, exportSchema = false)
abstract class HighScoreDatabase : RoomDatabase() {
    abstract fun highScoreDao(): HighScoreDao

    companion object {
        @Volatile
        private var INSTANCE: HighScoreDatabase? = null

        fun getDatabase(context: Context): HighScoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HighScoreDatabase::class.java,
                    "flappy_bird_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScore>> = highScoreDao.getTopScores()
    val personalBest: Flow<Int?> = highScoreDao.getPersonalBest()

    fun getPersonalBestForMode(mode: String): Flow<Int?> = highScoreDao.getPersonalBestForMode(mode)

    suspend fun insert(highScore: HighScore) {
        if (highScore.score > 0) {
            highScoreDao.insertScore(highScore)
        }
    }

    suspend fun clearAll() {
        highScoreDao.clearAllScores()
    }
}
