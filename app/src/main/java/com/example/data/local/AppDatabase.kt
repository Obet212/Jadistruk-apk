package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CharRule
import com.example.data.model.ColumnConfig
import com.example.data.model.ColumnFunction
import com.example.data.model.ColumnType
import com.example.data.model.ReceiptHistory
import com.example.data.model.SourceApp
import com.example.data.model.Template
import com.example.data.model.TextAlignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SourceApp::class,
        Template::class,
        ColumnConfig::class,
        ReceiptHistory::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceAppDao(): SourceAppDao
    abstract fun templateDao(): TemplateDao
    abstract fun columnConfigDao(): ColumnConfigDao
    abstract fun receiptHistoryDao(): ReceiptHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cetakin_receipt_database.db"
                )
                    .addCallback(AppDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }
    }
}

suspend fun populateInitialData(database: AppDatabase) {
    val sourceAppDao = database.sourceAppDao()
    val templateDao = database.templateDao()
    val columnConfigDao = database.columnConfigDao()

    if (sourceAppDao.getCount() > 0) return

    // 1. BCA Mobile
    val bcaAppId = sourceAppDao.insert(
        SourceApp(
            packageName = "com.bca",
            appName = "BCA Mobile",
            description = "m-BCA Transfer & Transaksi",
            iconColorHex = "#005EAD"
        )
    )
    val bcaTemplateId = templateDao.insert(
        Template(
            sourceAppId = bcaAppId,
            templateName = "Struk BCA Mobile",
            headerTitle = "BUKTI TRANSAKSI BCA",
            headerSubtitle = "LOKET PEMBAYARAN RESMI",
            footerNote = "Terima kasih telah bertransaksi.\nSimpan struk ini sebagai bukti pembayaran.",
            paperWidthMm = 58
        )
    )
    columnConfigDao.insertAll(
        listOf(
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Tanggal",
                columnType = ColumnType.AUTO.name,
                orderIndex = 1,
                columnFunction = ColumnFunction.DATE.name,
                charRule = CharRule.FREE_CHARS.name,
                normalizedX = 0.05f, normalizedY = 0.15f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Jam",
                columnType = ColumnType.AUTO.name,
                orderIndex = 2,
                columnFunction = ColumnFunction.TIME.name,
                charRule = CharRule.FREE_CHARS.name,
                normalizedX = 0.05f, normalizedY = 0.18f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "No. Referensi",
                columnType = ColumnType.AUTO.name,
                orderIndex = 3,
                columnFunction = ColumnFunction.REFERENCE.name,
                charRule = CharRule.LETTERS_AND_NUMBERS.name,
                normalizedX = 0.05f, normalizedY = 0.22f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Rekening Tujuan",
                columnType = ColumnType.AUTO.name,
                orderIndex = 4,
                columnFunction = ColumnFunction.ACCOUNT_NUMBER.name,
                charRule = CharRule.NUMBERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.32f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Nama Penerima",
                columnType = ColumnType.AUTO.name,
                orderIndex = 5,
                columnFunction = ColumnFunction.NAME.name,
                charRule = CharRule.LETTERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.38f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Jumlah Transfer",
                columnType = ColumnType.AUTO.name,
                orderIndex = 6,
                columnFunction = ColumnFunction.NOMINAL.name,
                charRule = CharRule.FREE_CHARS.name,
                normalizedX = 0.05f, normalizedY = 0.45f, normalizedWidth = 0.90f, normalizedHeight = 0.08f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Biaya Admin",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "Rp 2.500",
                orderIndex = 7,
                columnFunction = ColumnFunction.NOMINAL.name,
                charRule = CharRule.FREE_CHARS.name,
                alignment = TextAlignment.LEFT.name
            ),
            ColumnConfig(
                templateId = bcaTemplateId,
                name = "Status",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "BERHASIL",
                orderIndex = 8,
                columnFunction = ColumnFunction.FREE_TEXT.name,
                charRule = CharRule.LETTERS_ONLY.name,
                alignment = TextAlignment.LEFT.name
            )
        )
    )

    // 2. BRImo (BRI)
    val briAppId = sourceAppDao.insert(
        SourceApp(
            packageName = "id.co.bri.brimo",
            appName = "BRImo",
            description = "Bank BRI Mobile Banking",
            iconColorHex = "#004B97"
        )
    )
    val briTemplateId = templateDao.insert(
        Template(
            sourceAppId = briAppId,
            templateName = "Struk BRImo",
            headerTitle = "BUKTI TRANSAKSI BRIMO",
            headerSubtitle = "AGEN BRILINK / PEMBAYARAN",
            footerNote = "Struk ini merupakan bukti transaksi yang sah.",
            paperWidthMm = 58
        )
    )
    columnConfigDao.insertAll(
        listOf(
            ColumnConfig(
                templateId = briTemplateId,
                name = "Tanggal & Waktu",
                columnType = ColumnType.AUTO.name,
                orderIndex = 1,
                columnFunction = ColumnFunction.DATE.name,
                charRule = CharRule.FREE_CHARS.name,
                normalizedX = 0.05f, normalizedY = 0.16f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "No. Referensi",
                columnType = ColumnType.AUTO.name,
                orderIndex = 2,
                columnFunction = ColumnFunction.REFERENCE.name,
                charRule = CharRule.LETTERS_AND_NUMBERS.name,
                normalizedX = 0.05f, normalizedY = 0.22f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "Rekening Tujuan",
                columnType = ColumnType.AUTO.name,
                orderIndex = 3,
                columnFunction = ColumnFunction.ACCOUNT_NUMBER.name,
                charRule = CharRule.NUMBERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.30f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "Nama Penerima",
                columnType = ColumnType.AUTO.name,
                orderIndex = 4,
                columnFunction = ColumnFunction.NAME.name,
                charRule = CharRule.LETTERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.36f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "Nominal",
                columnType = ColumnType.AUTO.name,
                orderIndex = 5,
                columnFunction = ColumnFunction.NOMINAL.name,
                charRule = CharRule.FREE_CHARS.name,
                normalizedX = 0.05f, normalizedY = 0.44f, normalizedWidth = 0.90f, normalizedHeight = 0.08f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "Biaya Admin",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "Rp 3.000",
                orderIndex = 6,
                columnFunction = ColumnFunction.NOMINAL.name,
                alignment = TextAlignment.LEFT.name
            ),
            ColumnConfig(
                templateId = briTemplateId,
                name = "Status",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "SUKSES",
                orderIndex = 7,
                columnFunction = ColumnFunction.FREE_TEXT.name,
                alignment = TextAlignment.LEFT.name
            )
        )
    )

    // 3. DANA
    val danaAppId = sourceAppDao.insert(
        SourceApp(
            packageName = "id.dana",
            appName = "DANA Indonesia",
            description = "Dompet Digital DANA",
            iconColorHex = "#118EEA"
        )
    )
    val danaTemplateId = templateDao.insert(
        Template(
            sourceAppId = danaAppId,
            templateName = "Struk DANA",
            headerTitle = "BUKTI TRANSAKSI DANA",
            headerSubtitle = "PEMBAYARAN DIGITAL",
            footerNote = "Terima kasih atas pembayaran Anda.",
            paperWidthMm = 58
        )
    )
    columnConfigDao.insertAll(
        listOf(
            ColumnConfig(
                templateId = danaTemplateId,
                name = "Tanggal",
                columnType = ColumnType.AUTO.name,
                orderIndex = 1,
                columnFunction = ColumnFunction.DATE.name,
                normalizedX = 0.05f, normalizedY = 0.18f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = danaTemplateId,
                name = "ID Transaksi",
                columnType = ColumnType.AUTO.name,
                orderIndex = 2,
                columnFunction = ColumnFunction.REFERENCE.name,
                normalizedX = 0.05f, normalizedY = 0.24f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = danaTemplateId,
                name = "Nomor Tujuan",
                columnType = ColumnType.AUTO.name,
                orderIndex = 3,
                columnFunction = ColumnFunction.PHONE_NUMBER.name,
                charRule = CharRule.NUMBERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.32f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = danaTemplateId,
                name = "Penerima",
                columnType = ColumnType.AUTO.name,
                orderIndex = 4,
                columnFunction = ColumnFunction.NAME.name,
                charRule = CharRule.LETTERS_ONLY.name,
                normalizedX = 0.05f, normalizedY = 0.38f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = danaTemplateId,
                name = "Total Bayar",
                columnType = ColumnType.AUTO.name,
                orderIndex = 5,
                columnFunction = ColumnFunction.NOMINAL.name,
                normalizedX = 0.05f, normalizedY = 0.46f, normalizedWidth = 0.90f, normalizedHeight = 0.08f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = danaTemplateId,
                name = "Admin",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "Rp 2.000",
                orderIndex = 6,
                alignment = TextAlignment.LEFT.name
            )
        )
    )

    // 4. Default / Generic Template (for any unrecognized source app or gallery pick)
    val genericAppId = sourceAppDao.insert(
        SourceApp(
            packageName = "com.cetakin.generic",
            appName = "Aplikasi Lain / Struk Umum",
            description = "Template universal untuk struk apapun",
            iconColorHex = "#10B981"
        )
    )
    val genericTemplateId = templateDao.insert(
        Template(
            sourceAppId = genericAppId,
            templateName = "Struk Umum & Transfer",
            headerTitle = "BUKTI PEMBAYARAN",
            headerSubtitle = "STRUK RESMI",
            footerNote = "Terima kasih atas kepercayaan Anda.\nBarang/layanan yang dibeli tidak dapat ditukar.",
            isDefault = true,
            paperWidthMm = 58
        )
    )
    columnConfigDao.insertAll(
        listOf(
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Tanggal",
                columnType = ColumnType.AUTO.name,
                orderIndex = 1,
                columnFunction = ColumnFunction.DATE.name,
                normalizedX = 0.05f, normalizedY = 0.15f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Waktu",
                columnType = ColumnType.AUTO.name,
                orderIndex = 2,
                columnFunction = ColumnFunction.TIME.name,
                normalizedX = 0.05f, normalizedY = 0.20f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "No. Ref",
                columnType = ColumnType.AUTO.name,
                orderIndex = 3,
                columnFunction = ColumnFunction.REFERENCE.name,
                normalizedX = 0.05f, normalizedY = 0.25f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Tujuan / Rek",
                columnType = ColumnType.AUTO.name,
                orderIndex = 4,
                columnFunction = ColumnFunction.ACCOUNT_NUMBER.name,
                normalizedX = 0.05f, normalizedY = 0.32f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Nama",
                columnType = ColumnType.AUTO.name,
                orderIndex = 5,
                columnFunction = ColumnFunction.NAME.name,
                normalizedX = 0.05f, normalizedY = 0.38f, normalizedWidth = 0.90f, normalizedHeight = 0.06f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Nominal",
                columnType = ColumnType.AUTO.name,
                orderIndex = 6,
                columnFunction = ColumnFunction.NOMINAL.name,
                normalizedX = 0.05f, normalizedY = 0.46f, normalizedWidth = 0.90f, normalizedHeight = 0.08f,
                alignment = TextAlignment.RIGHT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Biaya Admin",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "Rp 2.000",
                orderIndex = 7,
                alignment = TextAlignment.LEFT.name
            ),
            ColumnConfig(
                templateId = genericTemplateId,
                name = "Keterangan",
                columnType = ColumnType.MANUAL.name,
                manualDefaultText = "LUNAS",
                orderIndex = 8,
                alignment = TextAlignment.LEFT.name
            )
        )
    )
}
