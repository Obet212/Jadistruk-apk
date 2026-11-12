package com.example.data.repository

import com.example.data.local.ColumnConfigDao
import com.example.data.local.ReceiptHistoryDao
import com.example.data.local.SourceAppDao
import com.example.data.local.TemplateDao
import com.example.data.model.ColumnConfig
import com.example.data.model.ReceiptHistory
import com.example.data.model.SourceApp
import com.example.data.model.Template
import kotlinx.coroutines.flow.Flow

class ReceiptRepository(
    private val sourceAppDao: SourceAppDao,
    private val templateDao: TemplateDao,
    private val columnConfigDao: ColumnConfigDao,
    private val receiptHistoryDao: ReceiptHistoryDao
) {
    // Source App
    val allSourceApps: Flow<List<SourceApp>> = sourceAppDao.getAllSourceApps()

    suspend fun getSourceAppByPackage(packageName: String): SourceApp? =
        sourceAppDao.getSourceAppByPackage(packageName)

    suspend fun getSourceAppById(id: Long): SourceApp? =
        sourceAppDao.getSourceAppByIdSync(id)

    suspend fun insertSourceApp(sourceApp: SourceApp): Long =
        sourceAppDao.insert(sourceApp)

    suspend fun updateSourceApp(sourceApp: SourceApp) =
        sourceAppDao.update(sourceApp)

    suspend fun deleteSourceApp(sourceApp: SourceApp) =
        sourceAppDao.delete(sourceApp)

    // Template
    val allTemplates: Flow<List<Template>> = templateDao.getAllTemplates()

    fun getTemplateForSourceApp(sourceAppId: Long): Flow<Template?> =
        templateDao.getTemplateForSourceApp(sourceAppId)

    suspend fun getTemplateForSourceAppSync(sourceAppId: Long): Template? =
        templateDao.getTemplateForSourceAppSync(sourceAppId)

    fun getTemplateById(id: Long): Flow<Template?> =
        templateDao.getTemplateById(id)

    suspend fun getTemplateByIdSync(id: Long): Template? =
        templateDao.getTemplateByIdSync(id)

    suspend fun insertTemplate(template: Template): Long =
        templateDao.insert(template)

    suspend fun updateTemplate(template: Template) =
        templateDao.update(template)

    suspend fun deleteTemplate(template: Template) =
        templateDao.delete(template)

    // Column Config
    fun getColumnsForTemplate(templateId: Long): Flow<List<ColumnConfig>> =
        columnConfigDao.getColumnsForTemplate(templateId)

    suspend fun getColumnsForTemplateSync(templateId: Long): List<ColumnConfig> =
        columnConfigDao.getColumnsForTemplateSync(templateId)

    suspend fun insertColumn(columnConfig: ColumnConfig): Long =
        columnConfigDao.insert(columnConfig)

    suspend fun insertAllColumns(columns: List<ColumnConfig>): List<Long> =
        columnConfigDao.insertAll(columns)

    suspend fun updateColumn(columnConfig: ColumnConfig) =
        columnConfigDao.update(columnConfig)

    suspend fun updateAllColumns(columns: List<ColumnConfig>) =
        columnConfigDao.updateAll(columns)

    suspend fun deleteColumn(columnConfig: ColumnConfig) =
        columnConfigDao.delete(columnConfig)

    suspend fun deleteColumnById(id: Long) =
        columnConfigDao.deleteById(id)

    // History
    val allHistory: Flow<List<ReceiptHistory>> = receiptHistoryDao.getAllHistory()

    fun getHistoryById(id: Long): Flow<ReceiptHistory?> =
        receiptHistoryDao.getHistoryById(id)

    suspend fun getHistoryByIdSync(id: Long): ReceiptHistory? =
        receiptHistoryDao.getHistoryByIdSync(id)

    suspend fun insertHistory(history: ReceiptHistory): Long =
        receiptHistoryDao.insert(history)

    suspend fun updateHistory(history: ReceiptHistory) =
        receiptHistoryDao.update(history)

    suspend fun deleteHistory(history: ReceiptHistory) =
        receiptHistoryDao.delete(history)

    suspend fun clearHistory() =
        receiptHistoryDao.clearAll()
}
