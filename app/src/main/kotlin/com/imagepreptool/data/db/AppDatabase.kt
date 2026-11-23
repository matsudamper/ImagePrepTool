package com.imagepreptool.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ProjectEntity::class,
        ProjectImageEntity::class,
        PenStrokeEntity::class,
        AppStateEntity::class,
        LastExportSettingsEntity::class,
        PenToolEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        fun open(file: File): AppDatabase {
            file.parentFile?.mkdirs()
            return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
