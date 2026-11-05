package com.hunter.training

import android.content.Context
import androidx.room.*

@Dao
interface HunterDao {
    @Query("SELECT * FROM exercises ORDER BY id") fun exercises(): List<Exercise>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun seed(exercises: List<Exercise>)
    @Query("SELECT * FROM routines ORDER BY created DESC, name ASC") fun routines(): List<Routine>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(routine: Routine)
    @Query("DELETE FROM routines WHERE id = :id") fun deleteRoutine(id: String)
    @Query("SELECT * FROM sessions ORDER BY time DESC") fun sessions(): List<Session>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun finish(session: Session): Long
}
@Database(entities = [Exercise::class, Routine::class, Session::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HunterDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "hunter.db")
                .build().also { instance = it }
        }
    }
}
