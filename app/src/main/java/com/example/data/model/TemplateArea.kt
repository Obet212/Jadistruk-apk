package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 7 Target fields defined for OBETSTRUK recipient transfers:
 * 1. WAKTU
 * 2. BANK
 * 3. REKENING
 * 4. PENERIMA
 * 5. NOMINAL
 * 6. ADMIN (manual)
 * 7. TOTAL (otomatis: NOMINAL + ADMIN)
 */
enum class TargetRecipientField(val fieldName: String, val description: String) {
  TANGGAL("TANGGAL", "Tanggal transaksi transfer (DD/MM/YYYY)"),
  WAKTU("WAKTU", "Waktu atau jam transaksi transfer (HH:MM:SS)"),
  REFERENSI("REFERENSI", "Nomor referensi / SN / kode transaksi"),
  BANK("BANK", "Nama bank atau dompet digital tujuan penerima"),
  REKENING("REKENING", "Nomor rekening atau nomor HP penerima (maks 15 digit)"),
  PENERIMA("PENERIMA", "Nama lengkap penerima transfer"),
  NOMINAL("NOMINAL", "Jumlah uang transfer diterima"),
  ADMIN("ADMIN", "Biaya admin transfer (diisi manual)"),
  TOTAL("TOTAL", "Total pembayaran/diterima (otomatis: Nominal + Admin)"),
}

/**
 * Bounding area within a template to extract a specific field value.
 * Values are normalized (0.0 to 1.0) so they adapt to any screenshot resolution.
 */
data class FieldBoundingArea(
  val id: String = UUID.randomUUID().toString(),
  val field: TargetRecipientField,
  val normLeft: Float,
  val normTop: Float,
  val normRight: Float,
  val normBottom: Float,
) {
  val normWidth: Float get() = (normRight - normLeft).coerceAtLeast(0f)
  val normHeight: Float get() = (normBottom - normTop).coerceAtLeast(0f)

  fun containsOrIntersects(box: OcrBoundingBox?): Boolean {
    if (box == null) return false
    val centerX = (box.normalizedLeft + box.normalizedRight) / 2f
    val centerY = (box.normalizedTop + box.normalizedBottom) / 2f
    if (centerX in normLeft..normRight && centerY in normTop..normBottom) {
      return true
    }

    // Check intersection
    val overlapLeft = maxOf(normLeft, box.normalizedLeft)
    val overlapTop = maxOf(normTop, box.normalizedTop)
    val overlapRight = minOf(normRight, box.normalizedRight)
    val overlapBottom = minOf(normBottom, box.normalizedBottom)
    if (overlapRight > overlapLeft && overlapBottom > overlapTop) {
      val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
      val boxArea = (box.normalizedRight - box.normalizedLeft) * (box.normalizedBottom - box.normalizedTop)
      if (boxArea > 0f && (overlapArea / boxArea) > 0.35f) {
        return true
      }
    }
    return false
  }
}

object AreaConfigHelper {
  fun toJson(areas: List<FieldBoundingArea>): String {
    val jsonArray = JSONArray()
    for (area in areas) {
      val obj = JSONObject().apply {
        put("id", area.id)
        put("field", area.field.name)
        put("left", area.normLeft.toDouble())
        put("top", area.normTop.toDouble())
        put("right", area.normRight.toDouble())
        put("bottom", area.normBottom.toDouble())
      }
      jsonArray.put(obj)
    }
    return jsonArray.toString()
  }

  fun fromJson(jsonStr: String): List<FieldBoundingArea> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<FieldBoundingArea>()
    try {
      val jsonArray = JSONArray(jsonStr)
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        val fieldName = obj.optString("field", TargetRecipientField.PENERIMA.name)
        val field = try {
          TargetRecipientField.valueOf(fieldName)
        } catch (_: Exception) {
          TargetRecipientField.PENERIMA
        }
        val id = obj.optString("id", UUID.randomUUID().toString())
        val left = obj.optDouble("left", 0.0).toFloat().coerceIn(0f, 1f)
        val top = obj.optDouble("top", 0.0).toFloat().coerceIn(0f, 1f)
        val right = obj.optDouble("right", 0.0).toFloat().coerceIn(0f, 1f)
        val bottom = obj.optDouble("bottom", 0.0).toFloat().coerceIn(0f, 1f)

        list.add(
          FieldBoundingArea(
            id = id,
            field = field,
            normLeft = minOf(left, right),
            normTop = minOf(top, bottom),
            normRight = maxOf(left, right),
            normBottom = maxOf(top, bottom)
          )
        )
      }
    } catch (_: Exception) {
      // Return whatever could be parsed
    }
    return list
  }

  /**
   * Extracts text from OCR result strictly inside the given area based on original image coordinates.
   */
  fun extractTextForArea(area: FieldBoundingArea, ocrResult: OcrResult): String {
    val lines = extractLinesForArea(area, ocrResult)
    if (lines.isNotEmpty()) {
      return lines.joinToString("\n") { it.text.trim() }.trim()
    }
    return ""
  }

  /**
   * Extracts structural OcrTextLine objects within the bounding area, maintaining bounding boxes and reading order.
   */
  fun extractLinesForArea(area: FieldBoundingArea, ocrResult: OcrResult): List<OcrTextLine> {
    val matchingLines = ocrResult.lines.filter { line ->
      area.containsOrIntersects(line.boundingBox)
    }

    if (matchingLines.isNotEmpty()) {
      return matchingLines.sortedWith(
        compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
      )
    }

    // Fallback: check text elements if lines were grouped broadly
    val matchingElements = mutableListOf<OcrTextElement>()
    for (line in ocrResult.lines) {
      for (elem in line.elements) {
        if (area.containsOrIntersects(elem.boundingBox)) {
          matchingElements.add(elem)
        }
      }
    }

    if (matchingElements.isNotEmpty()) {
      val sortedElements = matchingElements.sortedWith(
        compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 })
      )
      return listOf(
        OcrTextLine(
          text = sortedElements.joinToString(" ") { it.text.trim() }.trim(),
          boundingBox = sortedElements.firstOrNull()?.boundingBox,
          elements = sortedElements
        )
      )
    }

    return emptyList()
  }
}
