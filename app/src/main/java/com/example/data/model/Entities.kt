package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_apps",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class SourceApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val description: String = "",
    val iconColorHex: String = "#2563EB",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "templates",
    foreignKeys = [
        ForeignKey(
            entity = SourceApp::class,
            parentColumns = ["id"],
            childColumns = ["sourceAppId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sourceAppId"], unique = true)]
)
data class Template(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceAppId: Long,
    val templateName: String,
    val paperWidthMm: Int = 58, // 58 or 80
    val headerTitle: String = "STRUK TRANSAKSI",
    val headerSubtitle: String = "AGEN RESMI / LOKET PEMBAYARAN",
    val footerNote: String = "Terima kasih atas transaksi Anda.\nSimpan struk ini sebagai bukti pembayaran sah.",
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "column_configs",
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class ColumnConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val name: String,
    val columnType: String = ColumnType.AUTO.name, // MANUAL or AUTO
    val orderIndex: Int = 0,
    val isEnabled: Boolean = true,
    val manualDefaultText: String = "",
    val columnFunction: String = ColumnFunction.FREE_TEXT.name,
    val charRule: String = CharRule.FREE_CHARS.name,
    val maxCharLength: Int = 0, // 0 = unlimited
    // Coordinates normalized 0.0f .. 1.0f relative to image width & height
    val normalizedX: Float = 0.0f,
    val normalizedY: Float = 0.0f,
    val normalizedWidth: Float = 1.0f,
    val normalizedHeight: Float = 0.05f,
    // Per requirement: MANUAL wajib LEFT, AUTO wajib RIGHT (or label left, value right)
    val alignment: String = TextAlignment.RIGHT.name
) {
    val isManual: Boolean get() = columnType == ColumnType.MANUAL.name
    val isAuto: Boolean get() = columnType == ColumnType.AUTO.name
}

@Entity(tableName = "receipt_history")
data class ReceiptHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceAppName: String,
    val templateName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val extractedDataJson: String, // JSON map of key to value
    val rawOcrText: String = "",
    val isPrinted: Boolean = false
)
