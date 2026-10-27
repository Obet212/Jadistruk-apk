package com.example.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.example.data.model.OcrBoundingBox
import com.example.data.model.OcrResult
import com.example.data.model.OcrTextBlock
import com.example.data.model.OcrTextElement
import com.example.data.model.OcrTextLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTextRecognizer(private val context: Context) {
  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  /**
   * Processes the transfer receipt image using on-device Google ML Kit Text Recognition.
   * Extracts text, lines, elements, and precise bounding boxes for template mapping.
   */
  suspend fun processImageUri(imageUri: Uri): OcrResult =
    suspendCancellableCoroutine { continuation ->
      try {
        // 1. Verify image validity and dimensions
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
          context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
          }
        } catch (e: Exception) {
          if (continuation.isActive) {
            continuation.resumeWithException(
              IOException("Gambar bukti transfer tidak dapat dibuka. Pastikan file valid.", e)
            )
          }
          return@suspendCancellableCoroutine
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
          if (continuation.isActive) {
            continuation.resumeWithException(
              IOException("Gambar bukti transfer kosong atau rusak. Silakan pilih gambar lain dari galeri.")
            )
          }
          return@suspendCancellableCoroutine
        }

        // 2. Load InputImage with ML Kit
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val imgWidth = if (inputImage.width > 0) inputImage.width else boundsOptions.outWidth
        val imgHeight = if (inputImage.height > 0) inputImage.height else boundsOptions.outHeight

        recognizer.process(inputImage)
          .addOnSuccessListener { visionText ->
            if (continuation.isActive) {
              val ocrResult = mapVisionTextToOcrResult(
                visionText = visionText,
                imageWidth = imgWidth,
                imageHeight = imgHeight,
                rotationDegrees = inputImage.rotationDegrees
              )
              continuation.resume(ocrResult)
            }
          }
          .addOnFailureListener { e ->
            if (continuation.isActive) {
              continuation.resumeWithException(
                IOException("Gagal membaca teks dari gambar: ${e.localizedMessage ?: "Kesalahan ML Kit"}", e)
              )
            }
          }
      } catch (e: Exception) {
        if (continuation.isActive) {
          continuation.resumeWithException(e)
        }
      }
    }

  /**
   * Backward-compatible simple text extraction
   */
  suspend fun extractTextFromUri(imageUri: Uri): String {
    return processImageUri(imageUri).fullText
  }

  private fun mapVisionTextToOcrResult(
    visionText: Text,
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,
  ): OcrResult {
    val blockModels = mutableListOf<OcrTextBlock>()
    val allLineModels = mutableListOf<OcrTextLine>()

    for (block in visionText.textBlocks) {
      val blockBox = mapBoundingBox(block.boundingBox, imageWidth, imageHeight)
      val lineModels = mutableListOf<OcrTextLine>()

      for (line in block.lines) {
        val lineBox = mapBoundingBox(line.boundingBox, imageWidth, imageHeight)
        val elementModels = mutableListOf<OcrTextElement>()

        for (element in line.elements) {
          val elementBox = mapBoundingBox(element.boundingBox, imageWidth, imageHeight)
          elementModels.add(
            OcrTextElement(
              text = element.text,
              boundingBox = elementBox
            )
          )
        }

        val lineObj = OcrTextLine(
          text = line.text,
          boundingBox = lineBox,
          elements = elementModels
        )
        lineModels.add(lineObj)
        allLineModels.add(lineObj)
      }

      blockModels.add(
        OcrTextBlock(
          text = block.text,
          boundingBox = blockBox,
          lines = lineModels
        )
      )
    }

    return OcrResult(
      fullText = visionText.text,
      imageWidth = imageWidth,
      imageHeight = imageHeight,
      rotationDegrees = rotationDegrees,
      blocks = blockModels,
      lines = allLineModels
    )
  }

  private fun mapBoundingBox(box: Rect?, imgWidth: Int, imgHeight: Int): OcrBoundingBox? {
    if (box == null) return null
    val safeWidth = imgWidth.coerceAtLeast(1).toFloat()
    val safeHeight = imgHeight.coerceAtLeast(1).toFloat()

    val normLeft = (box.left.toFloat() / safeWidth).coerceIn(0f, 1f)
    val normTop = (box.top.toFloat() / safeHeight).coerceIn(0f, 1f)
    val normRight = (box.right.toFloat() / safeWidth).coerceIn(0f, 1f)
    val normBottom = (box.bottom.toFloat() / safeHeight).coerceIn(0f, 1f)

    return OcrBoundingBox(
      left = box.left,
      top = box.top,
      right = box.right,
      bottom = box.bottom,
      normalizedLeft = normLeft,
      normalizedTop = normTop,
      normalizedRight = normRight,
      normalizedBottom = normBottom
    )
  }

  fun close() {
    try {
      recognizer.close()
    } catch (_: Exception) {}
  }
}
