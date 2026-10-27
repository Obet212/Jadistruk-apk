package com.example.data.model

/**
 * Represents bounding box coordinates both in absolute pixels and normalized ratios (0.0 to 1.0).
 * Normalized coordinates allow resolution-independent mapping for OCR templates.
 */
data class OcrBoundingBox(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val normalizedLeft: Float,
  val normalizedTop: Float,
  val normalizedRight: Float,
  val normalizedBottom: Float,
) {
  val width: Int get() = (right - left).coerceAtLeast(0)
  val height: Int get() = (bottom - top).coerceAtLeast(0)

  val normalizedWidth: Float get() = (normalizedRight - normalizedLeft).coerceAtLeast(0f)
  val normalizedHeight: Float get() = (normalizedBottom - normalizedTop).coerceAtLeast(0f)
}

data class OcrTextElement(
  val text: String,
  val boundingBox: OcrBoundingBox?,
)

data class OcrTextLine(
  val text: String,
  val boundingBox: OcrBoundingBox?,
  val elements: List<OcrTextElement> = emptyList(),
)

data class OcrTextBlock(
  val text: String,
  val boundingBox: OcrBoundingBox?,
  val lines: List<OcrTextLine> = emptyList(),
)

data class OcrResult(
  val fullText: String,
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
  val rotationDegrees: Int = 0,
  val blocks: List<OcrTextBlock> = emptyList(),
  val lines: List<OcrTextLine> = emptyList(),
) {
  val isEmpty: Boolean get() = fullText.isBlank() && blocks.isEmpty()
}
