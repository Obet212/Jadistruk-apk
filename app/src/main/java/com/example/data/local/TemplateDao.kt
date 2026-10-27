package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
  @Query("SELECT * FROM ocr_templates ORDER BY isBuiltIn DESC, bankName ASC")
  fun getAllTemplates(): Flow<List<TemplateEntity>>

  @Query("SELECT * FROM ocr_templates WHERE id = :id LIMIT 1")
  suspend fun getTemplateById(id: Long): TemplateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTemplate(template: TemplateEntity): Long

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTemplates(templates: List<TemplateEntity>)

  @Update
  suspend fun updateTemplate(template: TemplateEntity)

  @Delete
  suspend fun deleteTemplate(template: TemplateEntity)

  @Query("SELECT COUNT(*) FROM ocr_templates")
  suspend fun getTemplateCount(): Int
}
