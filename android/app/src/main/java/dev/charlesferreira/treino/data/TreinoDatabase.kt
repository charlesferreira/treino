package dev.charlesferreira.treino.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutItemEntity::class,
        SessionEntity::class,
        SetEntryEntity::class,
        TimeEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TreinoDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun entryDao(): EntryDao

    companion object {
        const val FILE_NAME = "treino.db"

        fun open(context: Context): TreinoDatabase =
            Room.databaseBuilder(context.applicationContext, TreinoDatabase::class.java, FILE_NAME).build()

        /** For tests. */
        fun inMemory(context: Context): TreinoDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, TreinoDatabase::class.java).build()
    }
}
