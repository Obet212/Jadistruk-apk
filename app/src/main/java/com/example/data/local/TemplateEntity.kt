package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.OcrTemplate

@Entity(tableName = "ocr_templates")
data class TemplateEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val bankName: String,
  val category: String,
  val detectionKeywords: String,
  val recipientPattern: String,
  val accountPattern: String,
  val amountPattern: String,
  val isBuiltIn: Boolean = true,
  val areaConfigJson: String = "",
) {
  fun toModel(): OcrTemplate =
    OcrTemplate(
      id = id,
      bankName = bankName,
      category = category,
      detectionKeywords = detectionKeywords,
      recipientPattern = recipientPattern,
      accountPattern = accountPattern,
      amountPattern = amountPattern,
      isBuiltIn = isBuiltIn,
      areaConfigJson = areaConfigJson,
    )

  companion object {
    fun fromModel(model: OcrTemplate): TemplateEntity =
      TemplateEntity(
        id = model.id,
        bankName = model.bankName,
        category = model.category,
        detectionKeywords = model.detectionKeywords,
        recipientPattern = model.recipientPattern,
        accountPattern = model.accountPattern,
        amountPattern = model.amountPattern,
        isBuiltIn = model.isBuiltIn,
        areaConfigJson = model.areaConfigJson,
      )
  }
}
