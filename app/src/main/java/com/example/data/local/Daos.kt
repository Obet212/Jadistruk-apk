package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ColumnConfig
import com.example.data.model.ReceiptHistory
import com.example.data.model.SourceApp
import com.example.data.model.Template
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceAppDao {
    @Query("SELECT * FROM source_apps ORDER BY appName ASC")
    fun getAllSourceApps(): Flow<List<SourceApp>>

    @Query("SELECT * FROM source_apps WHERE id = :id LIMIT 1")
    fun getSourceAppById(id: Long): Flow<SourceApp?>

    @Query("SELECT * FROM source_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getSourceAppByPackage(packageName: String): SourceApp?

    @Query("SELECT * FROM source_apps WHERE id = :id LIMIT 1")
    suspend fun getSourceAppByIdSync(id: Long): SourceApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: SourceApp): Long

    @Update
    suspend fun update(app: SourceApp)

    @Delete
    suspend fun delete(app: SourceApp)

    @Query("SELECT COUNT(*) FROM source_apps")
    suspend fun getCount(): Int
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY templateName ASC")
    fun getAllTemplates(): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE sourceAppId = :sourceAppId LIMIT 1")
    fun getTemplateForSourceApp(sourceAppId: Long): Flow<Template?>

    @Query("SELECT * FROM templates WHERE sourceAppId = :sourceAppId LIMIT 1")
    suspend fun getTemplateForSourceAppSync(sourceAppId: Long): Template?

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    fun getTemplateById(id: Long): Flow<Template?>

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateByIdSync(id: Long): Template?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: Template): Long

    @Update
    suspend fun update(template: Template)

    @Delete
    suspend fun delete(template: Template)
}

@Dao
interface ColumnConfigDao {
    @Query("SELECT * FROM column_configs WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun getColumnsForTemplate(templateId: Long): Flow<List<ColumnConfig>>

    @Query("SELECT * FROM column_configs WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getColumnsForTemplateSync(templateId: Long): List<ColumnConfig>

    @Query("SELECT * FROM column_configs WHERE id = :id LIMIT 1")
    suspend fun getColumnById(id: Long): ColumnConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(column: ColumnConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(columns: List<ColumnConfig>): List<Long>

    @Update
    suspend fun update(column: ColumnConfig)

    @Update
    suspend fun updateAll(columns: List<ColumnConfig>)

    @Delete
    suspend fun delete(column: ColumnConfig)

    @Query("DELETE FROM column_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM column_configs WHERE templateId = :templateId")
    suspend fun deleteAllForTemplate(templateId: Long)
}

@Dao
interface ReceiptHistoryDao {
    @Query("SELECT * FROM receipt_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ReceiptHistory>>

    @Query("SELECT * FROM receipt_history WHERE id = :id LIMIT 1")
    fun getHistoryById(id: Long): Flow<ReceiptHistory?>

    @Query("SELECT * FROM receipt_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryByIdSync(id: Long): ReceiptHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ReceiptHistory): Long

    @Update
    suspend fun update(history: ReceiptHistory)

    @Delete
    suspend fun delete(history: ReceiptHistory)

    @Query("DELETE FROM receipt_history")
    suspend fun clearAll()
}
