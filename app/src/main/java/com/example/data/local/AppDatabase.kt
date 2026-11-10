package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE ocr_templates ADD COLUMN sampleImageUri TEXT NOT NULL DEFAULT ''")
  }
}

@Database(
  entities = [ReceiptEntity::class, TemplateEntity::class],
  version = 3,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun receiptDao(): ReceiptDao
  abstract fun templateDao(): TemplateDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "jadistruk_database"
        )
        .addMigrations(MIGRATION_2_3)
        .addCallback(object : Callback() {
          override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch(Dispatchers.IO) {
              INSTANCE?.templateDao()?.insertTemplates(DefaultTemplates.getDefaults())
            }
          }
          override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            scope.launch(Dispatchers.IO) {
              INSTANCE?.templateDao()?.insertTemplates(DefaultTemplates.getDefaults())
            }
          }
        })
        .fallbackToDestructiveMigration(true)
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
