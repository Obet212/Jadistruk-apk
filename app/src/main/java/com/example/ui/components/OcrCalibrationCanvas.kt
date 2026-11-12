package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.data.model.ColumnConfig
import com.example.engine.DetectedTextItem

@Composable
fun OcrCalibrationCanvas(
    bitmap: Bitmap,
    detectedItems: List<DetectedTextItem>,
    selectedColumn: ColumnConfig?,
    onBoxChanged: (normX: Float, normY: Float, normW: Float, normH: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val viewWidth = maxWidth
        val viewHeight = maxHeight

        var startOffset by remember { mutableStateOf<Offset?>(null) }
        var currentOffset by remember { mutableStateOf<Offset?>(null) }

        // Selected column current box normalized
        val colNormX = selectedColumn?.normalizedX ?: 0.1f
        val colNormY = selectedColumn?.normalizedY ?: 0.1f
        val colNormW = selectedColumn?.normalizedWidth ?: 0.8f
        val colNormH = selectedColumn?.normalizedHeight ?: 0.06f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedColumn) {
                    detectTapGestures { tapOffset ->
                        val normX = (tapOffset.x / size.width).coerceIn(0f, 1f)
                        val normY = (tapOffset.y / size.height).coerceIn(0f, 1f)

                        // Check if tap hit any recognized text item
                        val hit = detectedItems.firstOrNull { item ->
                            normX >= item.normalizedBox.left && normX <= item.normalizedBox.right &&
                                    normY >= item.normalizedBox.top && normY <= item.normalizedBox.bottom
                        }

                        if (hit != null) {
                            onBoxChanged(
                                hit.normalizedBox.left,
                                hit.normalizedBox.top,
                                hit.normalizedBox.width(),
                                hit.normalizedBox.height()
                            )
                        } else {
                            // Default small box around tap
                            val boxW = 0.5f
                            val boxH = 0.05f
                            val left = (normX - boxW / 2).coerceIn(0f, 1f - boxW)
                            val top = (normY - boxH / 2).coerceIn(0f, 1f - boxH)
                            onBoxChanged(left, top, boxW, boxH)
                        }
                    }
                }
                .pointerInput(selectedColumn) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startOffset = offset
                            currentOffset = offset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentOffset = change.position
                        },
                        onDragEnd = {
                            val start = startOffset
                            val current = currentOffset
                            if (start != null && current != null) {
                                val minX = minOf(start.x, current.x).coerceAtLeast(0f)
                                val maxX = maxOf(start.x, current.x).coerceAtMost(size.width.toFloat())
                                val minY = minOf(start.y, current.y).coerceAtLeast(0f)
                                val maxY = maxOf(start.y, current.y).coerceAtMost(size.height.toFloat())

                                val normX = minX / size.width
                                val normY = minY / size.height
                                val normW = ((maxX - minX) / size.width).coerceAtLeast(0.05f)
                                val normH = ((maxY - minY) / size.height).coerceAtLeast(0.02f)

                                onBoxChanged(normX, normY, normW, normH)
                            }
                            startOffset = null
                            currentOffset = null
                        },
                        onDragCancel = {
                            startOffset = null
                            currentOffset = null
                        }
                    )
                }
        ) {
            // Receipt image background
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Gambar Struk Asli",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Canvas drawing detected text boxes and active selection
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw all detected ML Kit items in subtle dashed or faint blue boxes
                for (item in detectedItems) {
                    val rect = item.normalizedBox
                    drawRect(
                        color = Color(0x403B82F6),
                        topLeft = Offset(rect.left * canvasWidth, rect.top * canvasHeight),
                        size = Size(rect.width() * canvasWidth, rect.height() * canvasHeight),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Draw the active column's calibrated target box (Bright Emerald Green)
                val activeLeft = colNormX * canvasWidth
                val activeTop = colNormY * canvasHeight
                val activeWidth = colNormW * canvasWidth
                val activeHeight = colNormH * canvasHeight

                // Shaded overlay inside target box
                drawRect(
                    color = Color(0x3310B981),
                    topLeft = Offset(activeLeft, activeTop),
                    size = Size(activeWidth, activeHeight)
                )

                // Strong outline
                drawRect(
                    color = Color(0xFF10B981),
                    topLeft = Offset(activeLeft, activeTop),
                    size = Size(activeWidth, activeHeight),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Dragging in-progress box
                val start = startOffset
                val current = currentOffset
                if (start != null && current != null) {
                    val minX = minOf(start.x, current.x)
                    val maxX = maxOf(start.x, current.x)
                    val minY = minOf(start.y, current.y)
                    val maxY = maxOf(start.y, current.y)

                    drawRect(
                        color = Color(0x55F59E0B),
                        topLeft = Offset(minX, minY),
                        size = Size(maxX - minX, maxY - minY)
                    )
                    drawRect(
                        color = Color(0xFFF59E0B),
                        topLeft = Offset(minX, minY),
                        size = Size(maxX - minX, maxY - minY),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }
}
