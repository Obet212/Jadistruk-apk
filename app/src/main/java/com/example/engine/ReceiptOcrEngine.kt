package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import com.example.data.model.CharRule
import com.example.data.model.ColumnConfig
import com.example.data.model.ColumnFunction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DetectedTextItem(
    val text: String,
    val normalizedBox: RectF, // 0.0f .. 1.0f relative to image width & height
    val confidence: Float = 1.0f
)

data class OcrExtractionResult(
    val rawFullText: String,
    val detectedItems: List<DetectedTextItem>,
    val columnValues: Map<Long, String>, // ColumnConfig.id -> extracted/normalized text
    val imageWidth: Int,
    val imageHeight: Int
)

object ReceiptOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs ML Kit OCR on a Bitmap and extracts text for each configured column.
     */
    suspend fun processImage(
        bitmap: Bitmap,
        columns: List<ColumnConfig>
    ): OcrExtractionResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = runMlKit(image)

        val imageWidth = bitmap.width.coerceAtLeast(1)
        val imageHeight = bitmap.height.coerceAtLeast(1)

        val detectedItems = mutableListOf<DetectedTextItem>()
        val fullTextBuilder = StringBuilder()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                fullTextBuilder.appendLine(line.text)
                val box = line.boundingBox
                val normalizedBox = if (box != null) {
                    RectF(
                        box.left.toFloat() / imageWidth,
                        box.top.toFloat() / imageHeight,
                        box.right.toFloat() / imageWidth,
                        box.bottom.toFloat() / imageHeight
                    )
                } else {
                    RectF(0f, 0f, 1f, 0.05f)
                }
                detectedItems.add(
                    DetectedTextItem(
                        text = line.text,
                        normalizedBox = normalizedBox,
                        confidence = line.confidence ?: 1.0f
                    )
                )
            }
        }

        // Now map columns to extracted text
        val columnValues = mutableMapOf<Long, String>()
        for (col in columns) {
            if (!col.isEnabled) continue

            if (col.isManual) {
                // Manual column uses user's default text or is left for user input
                columnValues[col.id] = EscPosCharacterNormalizer.normalizeText(col.manualDefaultText)
            } else {
                // Auto column: match by normalized coordinate box
                val matchedText = findBestMatchingText(
                    col = col,
                    items = detectedItems,
                    fullText = visionText.text
                )
                val normalizedValue = applyFunctionAndCharRules(matchedText, col)
                columnValues[col.id] = normalizedValue
            }
        }

        OcrExtractionResult(
            rawFullText = fullTextBuilder.toString(),
            detectedItems = detectedItems,
            columnValues = columnValues,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    private suspend fun runMlKit(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    /**
     * Finds text items overlapping or nearest to the column's normalized target coordinate box.
     */
    private fun findBestMatchingText(
        col: ColumnConfig,
        items: List<DetectedTextItem>,
        fullText: String
    ): String {
        val targetBox = RectF(
            col.normalizedX,
            col.normalizedY,
            col.normalizedX + col.normalizedWidth,
            col.normalizedY + col.normalizedHeight
        )

        // 1. Check intersecting bounding boxes
        val candidates = items.filter { item ->
            RectF.intersects(item.normalizedBox, targetBox) ||
                    (item.normalizedBox.centerY() >= targetBox.top && item.normalizedBox.centerY() <= targetBox.bottom)
        }

        if (candidates.isNotEmpty()) {
            // Sort by vertical position, then horizontal
            val sorted = candidates.sortedWith(
                compareBy<DetectedTextItem> { it.normalizedBox.top }.thenBy { it.normalizedBox.left }
            )
            return sorted.joinToString(" ") { it.text.trim() }
        }

        // 2. If no direct coordinate intersection, attempt smart keyword heuristic fallback
        val keywordFallback = findByKeywordHeuristic(col, items)
        if (keywordFallback.isNotEmpty()) {
            return keywordFallback
        }

        return ""
    }

    /**
     * Heuristic fallback if coordinate box wasn't calibrated yet:
     * finds lines matching column name keywords (e.g. "Nominal", "Tanggal", "Nama")
     * and extracts the neighboring value.
     */
    private fun findByKeywordHeuristic(col: ColumnConfig, items: List<DetectedTextItem>): String {
        val colName = col.name.lowercase().trim()
        val func = try { ColumnFunction.valueOf(col.columnFunction) } catch (_: Exception) { ColumnFunction.FREE_TEXT }

        for (i in items.indices) {
            val itemText = items[i].text
            val lower = itemText.lowercase()

            val matchesKeyword = when {
                colName.contains("nominal") || colName.contains("jumlah") || colName.contains("total") ->
                    lower.contains("nominal") || lower.contains("jumlah") || lower.contains("total") || lower.contains("rp")
                colName.contains("tanggal") || colName.contains("tgl") ->
                    lower.contains("tanggal") || lower.contains("tgl") || lower.contains("date")
                colName.contains("jam") || colName.contains("waktu") ->
                    lower.contains("jam") || lower.contains("waktu") || lower.contains("time")
                colName.contains("rekening") || colName.contains("rek") ->
                    lower.contains("rekening") || lower.contains("rek") || lower.contains("no. rek")
                colName.contains("penerima") || colName.contains("nama") ->
                    lower.contains("penerima") || lower.contains("nama") || lower.contains("ke:")
                colName.contains("ref") || colName.contains("transaksi") ->
                    lower.contains("ref") || lower.contains("id transaksi") || lower.contains("no.")
                else -> lower.contains(colName)
            }

            if (matchesKeyword) {
                // If the same line has a colon or separator (e.g. "Nominal: Rp 50.000")
                if (itemText.contains(":") || itemText.contains("Rp", ignoreCase = true)) {
                    val afterColon = itemText.substringAfter(":").trim()
                    if (afterColon.isNotEmpty() && afterColon != itemText) {
                        return afterColon
                    }
                }
                // Or check the next line or item nearby
                if (i + 1 < items.size) {
                    val next = items[i + 1].text.trim()
                    if (next.isNotEmpty() && !next.contains(":")) {
                        return next
                    }
                }
            }
        }

        return ""
    }

    /**
     * Applies column function normalization and character filter rules.
     */
    fun applyFunctionAndCharRules(rawText: String, col: ColumnConfig): String {
        var text = EscPosCharacterNormalizer.normalizeText(rawText).trim()
        if (text.isEmpty()) return ""

        val func = try {
            ColumnFunction.valueOf(col.columnFunction)
        } catch (_: Exception) {
            ColumnFunction.FREE_TEXT
        }

        // Apply Function-specific parsing & normalization
        text = when (func) {
            ColumnFunction.DATE -> normalizeDate(text)
            ColumnFunction.TIME -> normalizeTime(text)
            ColumnFunction.NAME -> normalizeName(text)
            ColumnFunction.ACCOUNT_NUMBER -> normalizeAccountNumber(text)
            ColumnFunction.PHONE_NUMBER -> normalizePhoneNumber(text)
            ColumnFunction.NOMINAL -> normalizeNominal(text)
            ColumnFunction.REFERENCE -> normalizeReference(text)
            ColumnFunction.NOTES -> normalizeNotes(text)
            ColumnFunction.FREE_TEXT -> text
        }

        // Apply Manual Char Rule filter
        val rule = try {
            CharRule.valueOf(col.charRule)
        } catch (_: Exception) {
            CharRule.FREE_CHARS
        }

        text = when (rule) {
            CharRule.LETTERS_ONLY -> text.filter { it.isLetter() || it.isWhitespace() }
            CharRule.NUMBERS_ONLY -> {
                // Strict check: keep all digits (including leading 0)
                text.filter { it.isDigit() }
            }
            CharRule.LETTERS_AND_NUMBERS -> text.filter { it.isLetterOrDigit() || it.isWhitespace() }
            CharRule.FREE_CHARS -> text
        }

        // Apply max length if set (> 0)
        if (col.maxCharLength > 0 && text.length > col.maxCharLength) {
            text = text.take(col.maxCharLength)
        }

        return text.trim()
    }

    private fun normalizeDate(input: String): String {
        // Find date pattern like 12/11/2026, 12-11-2026, 12.11.2026 or 12 Nov 2026
        val dateRegex = Regex("(\\d{1,2}[/\\-. ]\\d{1,2}[/\\-. ]\\d{2,4})|(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})")
        val match = dateRegex.find(input)
        if (match != null) {
            return match.value
        }
        // Fallback: keep digits, spaces, and date separators
        return input.filter { it.isDigit() || it == '/' || it == '-' || it == '.' || it.isWhitespace() }
    }

    private fun normalizeTime(input: String): String {
        // Find time pattern like 14:30, 14:30:15, 14.30 WIB
        val timeRegex = Regex("(\\d{1,2}[:.]\\d{2}([:.]\\d{2})?(\\s*(WIB|WITA|WIT|AM|PM))?)")
        val match = timeRegex.find(input)
        if (match != null) {
            return match.value
        }
        return input.filter { it.isDigit() || it == ':' || it == '.' || it.isWhitespace() }
    }

    private fun normalizeName(input: String): String {
        // Names: only letters and spaces, strip out labels like "Penerima:", "Ke:", etc.
        var cleaned = input
            .replace(Regex("(?i)^(penerima|nama|tujuan|pengirim|ke)[:\\s-]*"), "")
            .trim()
        cleaned = cleaned.filter { it.isLetter() || it.isWhitespace() }
        return cleaned.replace(Regex("\\s+"), " ")
    }

    private fun normalizeAccountNumber(input: String): String {
        // CRITICAL RULE: Preserve leading 0!
        // Extract digits and preserve full string
        var digits = input.filter { it.isDigit() }
        // If there were sensor asterisks e.g. 123****890, also preserve *
        if (digits.isEmpty()) {
            val withAsterisk = input.filter { it.isDigit() || it == '*' }
            if (withAsterisk.isNotEmpty()) return withAsterisk
        }
        return digits
    }

    private fun normalizePhoneNumber(input: String): String {
        // CRITICAL RULE: Keep 0 at front! E.g. +62812... -> 0812...
        var raw = input.trim()
        if (raw.startsWith("+62")) {
            raw = "0" + raw.removePrefix("+62")
        } else if (raw.startsWith("62") && raw.length >= 10) {
            raw = "0" + raw.removePrefix("62")
        }
        val digits = raw.filter { it.isDigit() }
        return if (digits.isNotEmpty()) digits else input
    }

    private fun normalizeNominal(input: String): String {
        // Nominal: e.g. Rp 50.000, 150.000,00, Rp. 1.250.000
        // Clean and maintain exact monetary representation without altering numbers
        var s = input.trim()
        // If it starts with Rp or contains Rp, keep it
        val hasRp = s.contains("Rp", ignoreCase = true)
        val numPart = s.filter { it.isDigit() || it == '.' || it == ',' }
        return if (numPart.isNotEmpty()) {
            if (hasRp && !numPart.startsWith("Rp", ignoreCase = true)) {
                "Rp $numPart"
            } else {
                if (!numPart.startsWith("Rp", ignoreCase = true)) "Rp $numPart" else numPart
            }
        } else {
            input
        }
    }

    private fun normalizeReference(input: String): String {
        // Reference: letters and digits (e.g. 20261112TRX98721, BCA-81928371)
        var s = input.replace(Regex("(?i)^(no\\.?\\s*ref(erensi)?|id\\s*transaksi)[:\\s-]*"), "").trim()
        return s.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun normalizeNotes(input: String): String {
        return input.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '.' }
    }

    /**
     * Loads a Bitmap safely from Uri.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
