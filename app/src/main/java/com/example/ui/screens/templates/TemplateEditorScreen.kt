package com.example.ui.screens.templates

import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AreaConfigHelper
import com.example.data.model.FieldBoundingArea
import com.example.data.model.OcrResult
import com.example.data.model.OcrTemplate
import com.example.data.model.TargetRecipientField
import com.example.data.ocr.MlKitTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

enum class AreaHandleType {
  NONE,
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  EDGE_TOP,
  EDGE_BOTTOM,
  EDGE_LEFT,
  EDGE_RIGHT,
  INSIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
  initialTemplate: OcrTemplate?,
  initialImageUri: Uri?,
  textRecognizer: MlKitTextRecognizer,
  onSaveTemplate: (OcrTemplate) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  var templateName by remember { mutableStateOf(initialTemplate?.bankName ?: "") }
  var category by remember { mutableStateOf(initialTemplate?.category ?: "Bank Transfer") }
  var detectionKeywords by remember { mutableStateOf(initialTemplate?.detectionKeywords ?: "") }
  var currentImageUri by remember { mutableStateOf<Uri?>(initialImageUri) }

  // Toolbar visibility state (Hide / Show Menu)
  var isToolbarVisible by remember { mutableStateOf(true) }

  // Dialogs
  var showEditNameDialog by remember { mutableStateOf(false) }
  var showAddFieldDialog by remember { mutableStateOf(false) }
  var showChangeFieldDialog by remember { mutableStateOf(false) }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }
  var showAllTestResultsDialog by remember { mutableStateOf(false) }

  // Areas list
  val areas = remember {
    val initialAreas = if (initialTemplate != null && initialTemplate.areaConfigJson.isNotBlank()) {
      AreaConfigHelper.fromJson(initialTemplate.areaConfigJson)
    } else {
      emptyList()
    }
    mutableStateOf(initialAreas)
  }

  var selectedAreaId by remember { mutableStateOf<String?>(areas.value.firstOrNull()?.id) }

  // Image native dimensions
  var imageWidth by remember { mutableStateOf(1080) }
  var imageHeight by remember { mutableStateOf(2400) }

  // Multi-touch Zoom & Pan states
  var zoomScale by remember { mutableFloatStateOf(1.0f) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }

  // Mode: Draw new box vs Select & Move
  var isDrawModeActive by remember { mutableStateOf(false) }
  var isDrawingNewArea by remember { mutableStateOf(false) }
  var pendingDrawField by remember { mutableStateOf<TargetRecipientField?>(null) }
  var drawStartNorm by remember { mutableStateOf<Offset?>(null) }
  var drawCurrentNorm by remember { mutableStateOf<Offset?>(null) }

  // Active interaction & 1-finger panning
  var activeHandle by remember { mutableStateOf(AreaHandleType.NONE) }
  var draggingAreaId by remember { mutableStateOf<String?>(null) }
  var lastTouchNorm by remember { mutableStateOf(Offset.Zero) }
  var is1FingerPanning by remember { mutableStateOf(false) }
  var lastTouchScreenPos by remember { mutableStateOf(Offset.Zero) }

  // Screen orientation support (Landscape / Portrait toggle)
  var isLandscape by remember { mutableStateOf(false) }
  val activity = context as? android.app.Activity

  DisposableEffect(Unit) {
    onDispose {
      activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  // Multi-touch pinch tracking
  var isMultiTouchActive by remember { mutableStateOf(false) }
  var prevCentroid by remember { mutableStateOf(Offset.Zero) }
  var prevPinchDistance by remember { mutableFloatStateOf(0f) }

  // OCR testing states
  var isOcrRunning by remember { mutableStateOf(false) }
  var cachedOcrResult by remember { mutableStateOf<OcrResult?>(null) }
  var singleAreaTestResult by remember { mutableStateOf<String?>(null) }
  val allTestResults = remember { mutableStateMapOf<TargetRecipientField, String>() }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        currentImageUri = uri
        cachedOcrResult = null
        // Reset zoom & pan on new image
        zoomScale = 1.0f
        panOffset = Offset.Zero
      }
    }
  )

  // Measure native image dimensions with EXIF orientation correction
  LaunchedEffect(currentImageUri) {
    val uri = currentImageUri ?: return@LaunchedEffect
    withContext(Dispatchers.IO) {
      try {
        var orientation = android.media.ExifInterface.ORIENTATION_NORMAL
        context.contentResolver.openInputStream(uri)?.use { stream ->
          try {
            val exif = android.media.ExifInterface(stream)
            orientation = exif.getAttributeInt(
              android.media.ExifInterface.TAG_ORIENTATION,
              android.media.ExifInterface.ORIENTATION_NORMAL
            )
          } catch (_: Exception) {}
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
          if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270
          ) {
            imageWidth = options.outHeight
            imageHeight = options.outWidth
          } else {
            imageWidth = options.outWidth
            imageHeight = options.outHeight
          }
        }
      } catch (_: Exception) {}
    }
  }

  BackHandler {
    showUnsavedChangesDialog = true
  }

  fun runOcrIfNeeded(onReady: (OcrResult) -> Unit) {
    val uri = currentImageUri
    if (uri == null) {
      Toast.makeText(context, "Pilih gambar bukti transfer terlebih dahulu", Toast.LENGTH_SHORT).show()
      return
    }
    if (cachedOcrResult != null) {
      onReady(cachedOcrResult!!)
      return
    }
    isOcrRunning = true
    scope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          textRecognizer.processImageUri(uri)
        }
        cachedOcrResult = result
        isOcrRunning = false
        onReady(result)
      } catch (e: Exception) {
        isOcrRunning = false
        Toast.makeText(context, "Gagal menjalankan OCR: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0F172A))
  ) {
    // 1. FULLSCREEN INTERACTIVE IMAGE & OCR CANVAS
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .clipToBounds()
    ) {
      val canvasWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
      val canvasHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

      val imgW = imageWidth.toFloat().coerceAtLeast(1f)
      val imgH = imageHeight.toFloat().coerceAtLeast(1f)

      val baseFitScale = minOf(canvasWidth / imgW, canvasHeight / imgH)
      val baseW = imgW * baseFitScale
      val baseH = imgH * baseFitScale
      val baseOffsetX = (canvasWidth - baseW) / 2f
      val baseOffsetY = (canvasHeight - baseH) / 2f

      val canvasCenterX = canvasWidth / 2f
      val canvasCenterY = canvasHeight / 2f

      // Coordinate converter helpers
      fun screenToNorm(screenPos: Offset): Offset {
        val baseX = canvasCenterX + (screenPos.x - canvasCenterX - panOffset.x) / zoomScale
        val baseY = canvasCenterY + (screenPos.y - canvasCenterY - panOffset.y) / zoomScale
        val u = ((baseX - baseOffsetX) / baseW).coerceIn(0f, 1f)
        val v = ((baseY - baseOffsetY) / baseH).coerceIn(0f, 1f)
        return Offset(u, v)
      }

      fun normToScreen(normPos: Offset): Offset {
        val baseX = baseOffsetX + normPos.x * baseW
        val baseY = baseOffsetY + normPos.y * baseH
        val screenX = canvasCenterX + (baseX - canvasCenterX) * zoomScale + panOffset.x
        val screenY = canvasCenterY + (baseY - canvasCenterY) * zoomScale + panOffset.y
        return Offset(screenX, screenY)
      }

      if (currentImageUri == null) {
        // Empty state when no screenshot is loaded
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clickable {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.AddPhotoAlternate,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.8f),
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Pilih Screenshot Bukti Transfer",
              color = Color.White,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Ketuk untuk memilih gambar dan mulai memetakan area OCR secara presisi layar penuh",
              color = Color.White.copy(alpha = 0.7f),
              style = MaterialTheme.typography.bodySmall,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              modifier = Modifier.padding(top = 6.dp)
            )
          }
        }
      } else {
        // Multi-touch Gesture Listener + Interactive Canvas
        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(areas.value, selectedAreaId, zoomScale, panOffset, isDrawModeActive, pendingDrawField) {
              awaitPointerEventScope {
                while (true) {
                  val event = awaitPointerEvent()
                  val pressedPointers = event.changes.filter { it.pressed }

                  if (pressedPointers.size >= 2) {
                    // MULTI-TOUCH 2-FINGER PINCH & PAN
                    isMultiTouchActive = true
                    activeHandle = AreaHandleType.NONE
                    draggingAreaId = null
                    isDrawingNewArea = false

                    val p1 = pressedPointers[0].position
                    val p2 = pressedPointers[1].position
                    val currentCentroid = (p1 + p2) / 2f
                    val currentDist = (p1 - p2).getDistance()

                    if (prevPinchDistance > 10f) {
                      val scaleRatio = currentDist / prevPinchDistance
                      val targetZoom = (zoomScale * scaleRatio).coerceIn(1.0f, 6.0f)
                      val effectiveRatio = targetZoom / zoomScale

                      // Invariant focal point formula
                      val newPan = (currentCentroid - Offset(canvasCenterX, canvasCenterY)) -
                        (prevCentroid - Offset(canvasCenterX, canvasCenterY) - panOffset) * effectiveRatio

                      zoomScale = targetZoom

                      val maxPanX = if (targetZoom > 1.0f) (baseW * targetZoom - canvasWidth).coerceAtLeast(0f) / 2f + 140.dp.toPx() else 0f
                      val maxPanY = if (targetZoom > 1.0f) (baseH * targetZoom - canvasHeight).coerceAtLeast(0f) / 2f + 140.dp.toPx() else 0f

                      panOffset = if (targetZoom <= 1.05f) {
                        Offset.Zero
                      } else {
                        Offset(
                          newPan.x.coerceIn(-maxPanX, maxPanX),
                          newPan.y.coerceIn(-maxPanY, maxPanY)
                        )
                      }
                    }

                    prevCentroid = currentCentroid
                    prevPinchDistance = currentDist

                    // Consume so child composables don't react
                    for (change in event.changes) {
                      change.consume()
                    }
                  } else if (pressedPointers.size == 1 && !isMultiTouchActive) {
                    // SINGLE-TOUCH: Draw, Move, or Resize
                    val singlePointer = pressedPointers[0]
                    val touchPos = singlePointer.position
                    val normTouch = screenToNorm(touchPos)

                    if (event.changes.any { it.previousPressed.not() && it.pressed }) {
                      // Pointer DOWN
                      if (isDrawModeActive) {
                        isDrawingNewArea = true
                        drawStartNorm = normTouch
                        drawCurrentNorm = normTouch
                      } else {
                        // Check if touching a resize handle on the currently selected area
                        val activeArea = areas.value.firstOrNull { it.id == selectedAreaId }
                        var hitHandle = AreaHandleType.NONE

                        if (activeArea != null) {
                          val handleHitRadius = 36.dp.toPx()
                          val sTL = normToScreen(Offset(activeArea.normLeft, activeArea.normTop))
                          val sTR = normToScreen(Offset(activeArea.normRight, activeArea.normTop))
                          val sBL = normToScreen(Offset(activeArea.normLeft, activeArea.normBottom))
                          val sBR = normToScreen(Offset(activeArea.normRight, activeArea.normBottom))

                          val sTop = Offset((sTL.x + sTR.x) / 2f, sTL.y)
                          val sBottom = Offset((sBL.x + sBR.x) / 2f, sBL.y)
                          val sLeft = Offset(sTL.x, (sTL.y + sBL.y) / 2f)
                          val sRight = Offset(sTR.x, (sTR.y + sBR.y) / 2f)

                          hitHandle = when {
                            (touchPos - sTL).getDistance() <= handleHitRadius -> AreaHandleType.TOP_LEFT
                            (touchPos - sTR).getDistance() <= handleHitRadius -> AreaHandleType.TOP_RIGHT
                            (touchPos - sBL).getDistance() <= handleHitRadius -> AreaHandleType.BOTTOM_LEFT
                            (touchPos - sBR).getDistance() <= handleHitRadius -> AreaHandleType.BOTTOM_RIGHT
                            (touchPos - sTop).getDistance() <= handleHitRadius -> AreaHandleType.EDGE_TOP
                            (touchPos - sBottom).getDistance() <= handleHitRadius -> AreaHandleType.EDGE_BOTTOM
                            (touchPos - sLeft).getDistance() <= handleHitRadius -> AreaHandleType.EDGE_LEFT
                            (touchPos - sRight).getDistance() <= handleHitRadius -> AreaHandleType.EDGE_RIGHT
                            normTouch.x in activeArea.normLeft..activeArea.normRight &&
                              normTouch.y in activeArea.normTop..activeArea.normBottom -> AreaHandleType.INSIDE
                            else -> AreaHandleType.NONE
                          }
                        }

                        if (hitHandle != AreaHandleType.NONE && activeArea != null) {
                          activeHandle = hitHandle
                          draggingAreaId = activeArea.id
                          lastTouchNorm = normTouch
                          singleAreaTestResult = null
                          is1FingerPanning = false
                        } else {
                          // Check if tapping inside another area
                          val otherHit = areas.value.firstOrNull { a ->
                            normTouch.x in a.normLeft..a.normRight && normTouch.y in a.normTop..a.normBottom
                          }
                          if (otherHit != null) {
                            selectedAreaId = otherHit.id
                            activeHandle = AreaHandleType.INSIDE
                            draggingAreaId = otherHit.id
                            lastTouchNorm = normTouch
                            singleAreaTestResult = null
                            is1FingerPanning = false
                          } else {
                            activeHandle = AreaHandleType.NONE
                            draggingAreaId = null
                            if (zoomScale > 1.05f) {
                              is1FingerPanning = true
                              lastTouchScreenPos = touchPos
                            } else {
                              is1FingerPanning = false
                            }
                          }
                        }
                      }
                      singlePointer.consume()
                    } else if (event.changes.any { it.pressed }) {
                      // Pointer MOVE
                      if (isDrawingNewArea) {
                        drawCurrentNorm = normTouch
                        singlePointer.consume()
                      } else if (activeHandle != AreaHandleType.NONE && draggingAreaId != null) {
                        val selId = draggingAreaId
                        val currentList = areas.value.toMutableList()
                        val idx = currentList.indexOfFirst { it.id == selId }
                        if (idx >= 0) {
                          val currentArea = currentList[idx]
                          when (activeHandle) {
                            AreaHandleType.INSIDE -> {
                              val deltaX = normTouch.x - lastTouchNorm.x
                              val deltaY = normTouch.y - lastTouchNorm.y
                              val w = currentArea.normWidth
                              val h = currentArea.normHeight
                              val newLeft = (currentArea.normLeft + deltaX).coerceIn(0f, 1f - w)
                              val newTop = (currentArea.normTop + deltaY).coerceIn(0f, 1f - h)
                              currentList[idx] = currentArea.copy(
                                normLeft = newLeft,
                                normTop = newTop,
                                normRight = newLeft + w,
                                normBottom = newTop + h
                              )
                              lastTouchNorm = normTouch
                            }
                            AreaHandleType.BOTTOM_RIGHT -> {
                              val newRight = normTouch.x.coerceIn(currentArea.normLeft + 0.02f, 1.0f)
                              val newBottom = normTouch.y.coerceIn(currentArea.normTop + 0.015f, 1.0f)
                              currentList[idx] = currentArea.copy(normRight = newRight, normBottom = newBottom)
                            }
                            AreaHandleType.TOP_LEFT -> {
                              val newLeft = normTouch.x.coerceIn(0f, currentArea.normRight - 0.02f)
                              val newTop = normTouch.y.coerceIn(0f, currentArea.normBottom - 0.015f)
                              currentList[idx] = currentArea.copy(normLeft = newLeft, normTop = newTop)
                            }
                            AreaHandleType.TOP_RIGHT -> {
                              val newRight = normTouch.x.coerceIn(currentArea.normLeft + 0.02f, 1.0f)
                              val newTop = normTouch.y.coerceIn(0f, currentArea.normBottom - 0.015f)
                              currentList[idx] = currentArea.copy(normRight = newRight, normTop = newTop)
                            }
                            AreaHandleType.BOTTOM_LEFT -> {
                              val newLeft = normTouch.x.coerceIn(0f, currentArea.normRight - 0.02f)
                              val newBottom = normTouch.y.coerceIn(currentArea.normTop + 0.015f, 1.0f)
                              currentList[idx] = currentArea.copy(normLeft = newLeft, normBottom = newBottom)
                            }
                            AreaHandleType.EDGE_TOP -> {
                              val newTop = normTouch.y.coerceIn(0f, currentArea.normBottom - 0.015f)
                              currentList[idx] = currentArea.copy(normTop = newTop)
                            }
                            AreaHandleType.EDGE_BOTTOM -> {
                              val newBottom = normTouch.y.coerceIn(currentArea.normTop + 0.015f, 1.0f)
                              currentList[idx] = currentArea.copy(normBottom = newBottom)
                            }
                            AreaHandleType.EDGE_LEFT -> {
                              val newLeft = normTouch.x.coerceIn(0f, currentArea.normRight - 0.02f)
                              currentList[idx] = currentArea.copy(normLeft = newLeft)
                            }
                            AreaHandleType.EDGE_RIGHT -> {
                              val newRight = normTouch.x.coerceIn(currentArea.normLeft + 0.02f, 1.0f)
                              currentList[idx] = currentArea.copy(normRight = newRight)
                            }
                            else -> {}
                          }
                          areas.value = currentList
                        }
                        singlePointer.consume()
                      } else if (is1FingerPanning && zoomScale > 1.05f) {
                        val delta = touchPos - lastTouchScreenPos
                        val maxPanX = (baseW * zoomScale - canvasWidth).coerceAtLeast(0f) / 2f + 140.dp.toPx()
                        val maxPanY = (baseH * zoomScale - canvasHeight).coerceAtLeast(0f) / 2f + 140.dp.toPx()
                        panOffset = Offset(
                          (panOffset.x + delta.x).coerceIn(-maxPanX, maxPanX),
                          (panOffset.y + delta.y).coerceIn(-maxPanY, maxPanY)
                        )
                        lastTouchScreenPos = touchPos
                        singlePointer.consume()
                      }
                    }
                  } else if (pressedPointers.isEmpty()) {
                    // All fingers lifted
                    is1FingerPanning = false
                    if (isMultiTouchActive) {
                      isMultiTouchActive = false
                      prevPinchDistance = 0f
                    }

                    if (isDrawingNewArea && drawStartNorm != null && drawCurrentNorm != null) {
                      val start = drawStartNorm!!
                      val curr = drawCurrentNorm!!
                      val left = minOf(start.x, curr.x).coerceIn(0f, 1f)
                      val right = maxOf(start.x, curr.x).coerceIn(0f, 1f)
                      val top = minOf(start.y, curr.y).coerceIn(0f, 1f)
                      val bottom = maxOf(start.y, curr.y).coerceIn(0f, 1f)

                      if ((right - left) >= 0.02f && (bottom - top) >= 0.015f) {
                        val field = pendingDrawField ?: run {
                          val used = areas.value.map { it.field }.toSet()
                          TargetRecipientField.entries.firstOrNull { it !in used } ?: TargetRecipientField.PENERIMA
                        }
                        val newArea = FieldBoundingArea(
                          id = UUID.randomUUID().toString(),
                          field = field,
                          normLeft = left,
                          normTop = top,
                          normRight = right,
                          normBottom = bottom
                        )
                        areas.value = areas.value + newArea
                        selectedAreaId = newArea.id
                        Toast.makeText(context, "Area ${field.fieldName} berhasil digambar!", Toast.LENGTH_SHORT).show()
                      }
                      isDrawingNewArea = false
                      drawStartNorm = null
                      drawCurrentNorm = null
                      isDrawModeActive = false
                      pendingDrawField = null
                    }

                    activeHandle = AreaHandleType.NONE
                    draggingAreaId = null
                  }
                }
              }
            }
        ) {
          // Hardware-accelerated Transformation Node (Image + Canvas)
          Box(
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
                transformOrigin = TransformOrigin(0.5f, 0.5f)
              }
          ) {
            // Fitted Background Image
            AsyncImage(
              model = currentImageUri,
              contentDescription = "Contoh Bukti Transfer",
              modifier = Modifier
                .offset { IntOffset(baseOffsetX.roundToInt(), baseOffsetY.roundToInt()) }
                .size(
                  width = with(density) { baseW.toDp() },
                  height = with(density) { baseH.toDp() }
                ),
              contentScale = ContentScale.FillBounds
            )

            // Canvas drawing all OCR boxes and handles
            Canvas(modifier = Modifier.fillMaxSize()) {
              for (area in areas.value) {
                val isSelected = area.id == selectedAreaId
                val boxX = baseOffsetX + area.normLeft * baseW
                val boxY = baseOffsetY + area.normTop * baseH
                val boxW = area.normWidth * baseW
                val boxH = area.normHeight * baseH

                val color = getFieldColor(area.field)

                // Fill translucent
                drawRect(
                  color = color.copy(alpha = if (isSelected) 0.35f else 0.18f),
                  topLeft = Offset(boxX, boxY),
                  size = Size(boxW, boxH)
                )

                // Outer border
                drawRect(
                  color = color,
                  topLeft = Offset(boxX, boxY),
                  size = Size(boxW, boxH),
                  style = Stroke(width = if (isSelected) 3.5.dp.toPx() / zoomScale else 2.dp.toPx() / zoomScale)
                )

                // Handles if selected
                if (isSelected) {
                  val handleRadius = (7.dp.toPx() / zoomScale).coerceAtLeast(3.5.dp.toPx())
                  val handleStroke = (2.dp.toPx() / zoomScale).coerceAtLeast(1.dp.toPx())

                  // 4 Corner handles
                  val corners = listOf(
                    Offset(boxX, boxY), // TL
                    Offset(boxX + boxW, boxY), // TR
                    Offset(boxX, boxY + boxH), // BL
                    Offset(boxX + boxW, boxY + boxH) // BR
                  )

                  for (pt in corners) {
                    drawCircle(color = Color.White, radius = handleRadius, center = pt)
                    drawCircle(color = color, radius = handleRadius, center = pt, style = Stroke(width = handleStroke))
                  }

                  // 4 Edge handles (midpoints)
                  val edges = listOf(
                    Offset(boxX + boxW / 2f, boxY), // Top
                    Offset(boxX + boxW / 2f, boxY + boxH), // Bottom
                    Offset(boxX, boxY + boxH / 2f), // Left
                    Offset(boxX + boxW, boxY + boxH / 2f) // Right
                  )

                  for (pt in edges) {
                    drawCircle(color = Color.White, radius = handleRadius * 0.8f, center = pt)
                    drawCircle(color = color, radius = handleRadius * 0.8f, center = pt, style = Stroke(width = handleStroke))
                  }
                }
              }

              // Real-time drawing feedback
              if (isDrawingNewArea && drawStartNorm != null && drawCurrentNorm != null) {
                val start = drawStartNorm!!
                val curr = drawCurrentNorm!!
                val left = minOf(start.x, curr.x)
                val right = maxOf(start.x, curr.x)
                val top = minOf(start.y, curr.y)
                val bottom = maxOf(start.y, curr.y)

                val boxX = baseOffsetX + left * baseW
                val boxY = baseOffsetY + top * baseH
                val boxW = (right - left) * baseW
                val boxH = (bottom - top) * baseH

                val drawColor = Color(0xFFF59E0B)
                drawRect(
                  color = drawColor.copy(alpha = 0.28f),
                  topLeft = Offset(boxX, boxY),
                  size = Size(boxW, boxH)
                )
                drawRect(
                  color = drawColor,
                  topLeft = Offset(boxX, boxY),
                  size = Size(boxW, boxH),
                  style = Stroke(width = 2.5.dp.toPx() / zoomScale)
                )
              }
            }
          }
        }
      }
    }

    // 2. TOP TOOLBAR (ALL MENU BUTTONS AT THE TOP)
    AnimatedVisibility(
      visible = isToolbarVisible,
      enter = slideInVertically { -it } + fadeIn(),
      exit = slideOutVertically { -it } + fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter)
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Row 1: TopAppBar with Primary Actions
          TopAppBar(
            title = {
              Column(
                modifier = Modifier
                  .clickable { showEditNameDialog = true }
                  .padding(vertical = 4.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = templateName.ifBlank { "Template Baru" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Nama",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                  )
                }
                Text(
                  text = "${areas.value.size}/7 Area • Zoom ${String.format("%.1fx", zoomScale)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1
                )
              }
            },
            navigationIcon = {
              IconButton(onClick = { showUnsavedChangesDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
              }
            },
            actions = {
              // Rotasi Orientasi Layar (Lanskap / Potret)
              IconButton(
                onClick = {
                  isLandscape = !isLandscape
                  activity?.requestedOrientation = if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                  } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                  }
                },
                modifier = Modifier.testTag("btn_toggle_orientation")
              ) {
                Icon(
                  imageVector = Icons.Filled.ScreenRotation,
                  contentDescription = if (isLandscape) "Ubah ke Mode Potret" else "Ubah ke Mode Lanskap",
                  tint = if (isLandscape) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
              }

              // Ganti/Pilih Screenshot
              IconButton(
                onClick = {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.testTag("btn_change_image_editor")
              ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Pilih / Ganti Gambar")
              }

              // Simpan Template
              IconButton(
                onClick = {
                  if (templateName.isBlank()) {
                    showEditNameDialog = true
                    Toast.makeText(context, "Mohon beri nama template (misal: BCA Mobile, DANA)", Toast.LENGTH_SHORT).show()
                    return@IconButton
                  }
                  if (areas.value.isEmpty()) {
                    Toast.makeText(context, "Tentukan minimal 1 area OCR pada gambar", Toast.LENGTH_SHORT).show()
                    return@IconButton
                  }

                  val areaJson = AreaConfigHelper.toJson(areas.value)
                  val updated = (initialTemplate ?: OcrTemplate(
                    bankName = templateName.trim(),
                    category = category,
                    detectionKeywords = detectionKeywords.ifBlank { templateName.trim() },
                    recipientPattern = "",
                    accountPattern = "",
                    amountPattern = "",
                    isBuiltIn = false,
                    areaConfigJson = areaJson
                  )).copy(
                    bankName = templateName.trim(),
                    category = category,
                    detectionKeywords = detectionKeywords.ifBlank { templateName.trim() },
                    areaConfigJson = areaJson,
                    isBuiltIn = false
                  )

                  onSaveTemplate(updated)
                  Toast.makeText(context, "Template ${templateName} berhasil disimpan!", Toast.LENGTH_SHORT).show()
                  onBack()
                },
                modifier = Modifier.testTag("btn_save_template_editor")
              ) {
                Icon(Icons.Filled.Save, contentDescription = "Simpan Template", tint = MaterialTheme.colorScheme.primary)
              }

              // SEMBUNYIKAN MENU BUTTON
              IconButton(
                onClick = { isToolbarVisible = false },
                modifier = Modifier.testTag("btn_hide_menu")
              ) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = "Sembunyikan Menu")
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
          )

          // Row 2: Secondary Toolbar with Horizontal Flow (Responsive, No Text Wrapping)
          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            item {
              Button(
                onClick = { showAddFieldDialog = true },
                enabled = areas.value.size < 7 && currentImageUri != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_add_area")
              ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Area (${areas.value.size}/7)", fontSize = 12.sp, maxLines = 1)
              }
            }

            item {
              OutlinedButton(
                onClick = {
                  isDrawModeActive = !isDrawModeActive
                  if (isDrawModeActive) {
                    Toast.makeText(context, "Mode Gambar: seret 1 jari untuk membuat kotak", Toast.LENGTH_SHORT).show()
                  }
                },
                enabled = areas.value.size < 7 && currentImageUri != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = if (isDrawModeActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
              ) {
                Icon(
                  imageVector = if (isDrawModeActive) Icons.Filled.CropFree else Icons.Filled.TouchApp,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isDrawModeActive) "Menggambar..." else "Tarik Kotak", fontSize = 12.sp, maxLines = 1)
              }
            }

            if (currentImageUri != null && areas.value.isNotEmpty()) {
              item {
                OutlinedButton(
                  onClick = {
                    runOcrIfNeeded { ocr ->
                      allTestResults.clear()
                      for (area in areas.value) {
                        val lines = AreaConfigHelper.extractLinesForArea(area, ocr)
                        val rawText = AreaConfigHelper.extractTextForArea(area, ocr)
                        val multi = com.example.data.ocr.MultiCategoryExtractor.extractFromLines(lines, area.field, area.id)
                        val detected = mutableListOf<String>()
                        multi.waktu?.let { detected.add("• WAKTU: ${it.value}") }
                        multi.bank?.let { detected.add("• BANK: ${it.value}") }
                        multi.rekening?.let { detected.add("• REKENING: ${it.value}") }
                        multi.penerima?.let { detected.add("• PENERIMA: ${it.value}") }
                        multi.nominal?.let { detected.add("• NOMINAL: Rp ${it.value}") }

                        val summary = if (detected.isNotEmpty()) {
                          "Teks:\n${rawText.ifBlank { "(Kosong)" }}\n\nKategori Terdeteksi:\n${detected.joinToString("\n")}"
                        } else {
                          rawText.ifBlank { "(Teks tidak terdeteksi)" }
                        }
                        allTestResults[area.field] = summary
                      }
                      showAllTestResultsDialog = true
                    }
                  },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.testTag("btn_test_all_areas")
                ) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Uji Semua OCR", fontSize = 12.sp, maxLines = 1)
                }
              }
            }

            val activeArea = areas.value.firstOrNull { it.id == selectedAreaId }
            if (activeArea != null) {
              item {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = getFieldColor(activeArea.field).copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 2.dp)
                  ) {
                    Text(
                      text = activeArea.field.fieldName,
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                      color = getFieldColor(activeArea.field),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                  }

                  IconButton(
                    onClick = { showChangeFieldDialog = true },
                    modifier = Modifier.size(34.dp)
                  ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Ganti Fungsi Kolom", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                  }

                  IconButton(
                    onClick = {
                      runOcrIfNeeded { ocr ->
                        val lines = AreaConfigHelper.extractLinesForArea(activeArea, ocr)
                        val rawText = AreaConfigHelper.extractTextForArea(activeArea, ocr)
                        val multi = com.example.data.ocr.MultiCategoryExtractor.extractFromLines(lines, activeArea.field, activeArea.id)
                        val detected = mutableListOf<String>()
                        multi.waktu?.let { detected.add("WAKTU: ${it.value}") }
                        multi.bank?.let { detected.add("BANK: ${it.value}") }
                        multi.rekening?.let { detected.add("REKENING: ${it.value}") }
                        multi.penerima?.let { detected.add("PENERIMA: ${it.value}") }
                        multi.nominal?.let { detected.add("NOMINAL: Rp ${it.value}") }

                        singleAreaTestResult = if (detected.isNotEmpty()) {
                          "$rawText\n\n[Deteksi Multi-Kategori]:\n${detected.joinToString("\n")}"
                        } else {
                          rawText.ifBlank { "TIDAK_ADA_TEKS" }
                        }
                      }
                    },
                    modifier = Modifier.size(34.dp)
                  ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Uji Area Ini", tint = getFieldColor(activeArea.field), modifier = Modifier.size(18.dp))
                  }

                  IconButton(
                    onClick = {
                      areas.value = areas.value.filter { it.id != activeArea.id }
                      selectedAreaId = areas.value.firstOrNull()?.id
                      singleAreaTestResult = null
                    },
                    modifier = Modifier.size(34.dp)
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus Area", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }
          }

          // Row 3: Horizontal list of defined area chips
          if (areas.value.isNotEmpty()) {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(areas.value) { area ->
                val isSelected = area.id == selectedAreaId
                val fieldColor = getFieldColor(area.field)
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    selectedAreaId = area.id
                    singleAreaTestResult = null
                  },
                  label = {
                    Text(
                      text = area.field.fieldName,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                  },
                  leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                  } else null,
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = fieldColor.copy(alpha = 0.25f),
                    selectedLabelColor = fieldColor
                  )
                )
              }
            }
          }

          // Row 4: Single Area OCR Result Display (if tested)
          if (singleAreaTestResult != null && selectedAreaId != null) {
            val activeArea = areas.value.firstOrNull { it.id == selectedAreaId }
            if (activeArea != null) {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (singleAreaTestResult == "TIDAK_ADA_TEKS") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                      imageVector = if (singleAreaTestResult == "TIDAK_ADA_TEKS") Icons.Filled.Close else Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = if (singleAreaTestResult == "TIDAK_ADA_TEKS") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (singleAreaTestResult == "TIDAK_ADA_TEKS") {
                        "[${activeArea.field.fieldName}] Tidak ada teks terdeteksi. Pindahkan kotak ke atas teks."
                      } else {
                        "[${activeArea.field.fieldName}] OCR: \"$singleAreaTestResult\""
                      },
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                      color = if (singleAreaTestResult == "TIDAK_ADA_TEKS") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                  IconButton(
                    onClick = { singleAreaTestResult = null },
                    modifier = Modifier.size(20.dp)
                  ) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }
        }
      }
    }

    // 3. FLOATING UNHIDE PILL BUTTON (WHEN MENU IS HIDDEN)
    if (!isToolbarVisible) {
      Surface(
        onClick = { isToolbarVisible = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 6.dp,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 16.dp)
          .testTag("btn_show_menu_floating")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Tampilkan Menu Editor",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    // 4. FLOATING RESET ZOOM BUTTON (WHEN ZOOMED IN)
    if (zoomScale > 1.05f) {
      Surface(
        onClick = {
          zoomScale = 1.0f
          panOffset = Offset.Zero
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        shadowElevation = 4.dp,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Reset Zoom (1.0x)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
          )
        }
      }
    }

    // 5. OCR RUNNING OVERLAY
    if (isOcrRunning) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "Membaca teks on-device ML Kit...",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
          }
        }
      }
    }
  }

  // DIALOG: Edit Template Name & Category
  if (showEditNameDialog) {
    var tempName by remember { mutableStateOf(templateName) }
    var tempCategory by remember { mutableStateOf(category) }
    var tempKeywords by remember { mutableStateOf(detectionKeywords) }

    AlertDialog(
      onDismissRequest = { showEditNameDialog = false },
      title = { Text("Nama & Pengaturan Template") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = tempName,
            onValueChange = { tempName = it },
            label = { Text("Nama Template (misal: BCA Mobile, DANA) *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = tempKeywords,
            onValueChange = { tempKeywords = it },
            label = { Text("Kata Kunci Deteksi (dipisah koma)") },
            singleLine = true,
            supportingText = { Text("Kata unik pada bukti transfer untuk deteksi otomatis") },
            modifier = Modifier.fillMaxWidth()
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = tempCategory == "Bank Transfer",
              onClick = { tempCategory = "Bank Transfer" },
              label = { Text("Bank Transfer") }
            )
            FilterChip(
              selected = tempCategory == "E-Wallet",
              onClick = { tempCategory = "E-Wallet" },
              label = { Text("E-Wallet") }
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            templateName = tempName.trim()
            category = tempCategory
            detectionKeywords = tempKeywords.trim()
            showEditNameDialog = false
          }
        ) {
          Text("Simpan")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditNameDialog = false }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG: Add Field Area
  if (showAddFieldDialog) {
    val usedFields = areas.value.map { it.field }.toSet()

    AlertDialog(
      onDismissRequest = { showAddFieldDialog = false },
      title = { Text("Tambah Area OCR (Maks. 7)") },
      text = {
        Column {
          Text(
            text = "Pilih kolom data penerima yang ingin dipetakan:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TargetRecipientField.entries) { field ->
              val isAlreadyUsed = field in usedFields
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable(enabled = !isAlreadyUsed) {
                    val count = areas.value.size
                    val topOffset = 0.15f + (count * 0.08f).coerceAtMost(0.65f)
                    val newArea = FieldBoundingArea(
                      id = UUID.randomUUID().toString(),
                      field = field,
                      normLeft = 0.10f,
                      normTop = topOffset,
                      normRight = 0.90f,
                      normBottom = topOffset + 0.055f
                    )
                    areas.value = areas.value + newArea
                    selectedAreaId = newArea.id
                    showAddFieldDialog = false
                    Toast.makeText(context, "Area ${field.fieldName} ditambahkan. Geser & sesuaikan posisinya di atas gambar.", Toast.LENGTH_LONG).show()
                  },
                colors = CardDefaults.cardColors(
                  containerColor = if (isAlreadyUsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, getFieldColor(field).copy(alpha = if (isAlreadyUsed) 0.3f else 0.8f)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text(
                      text = "${field.ordinal + 1}. ${field.fieldName}",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = if (isAlreadyUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else getFieldColor(field)
                    )
                    Text(
                      text = field.description,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isAlreadyUsed) 0.4f else 1f)
                    )
                  }
                  if (isAlreadyUsed) {
                    Text("Sudah Ada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showAddFieldDialog = false }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG: Change Field for Selected Area
  if (showChangeFieldDialog) {
    val activeArea = areas.value.firstOrNull { it.id == selectedAreaId }
    val usedFields = areas.value.map { it.field }.toSet()

    AlertDialog(
      onDismissRequest = { showChangeFieldDialog = false },
      title = { Text("Ganti Fungsi Kolom") },
      text = {
        Column {
          Text(
            text = "Ubah fungsi data untuk area ini:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TargetRecipientField.entries) { field ->
              val isAlreadyUsed = field in usedFields && field != activeArea?.field
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable(enabled = !isAlreadyUsed) {
                    if (activeArea != null) {
                      val currentList = areas.value.toMutableList()
                      val idx = currentList.indexOfFirst { it.id == activeArea.id }
                      if (idx >= 0) {
                        currentList[idx] = activeArea.copy(field = field)
                        areas.value = currentList
                      }
                    }
                    showChangeFieldDialog = false
                  },
                colors = CardDefaults.cardColors(
                  containerColor = if (isAlreadyUsed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "${field.ordinal + 1}. ${field.fieldName}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isAlreadyUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else getFieldColor(field)
                  )
                  if (activeArea?.field == field) {
                    Text("Saat Ini", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showChangeFieldDialog = false }) {
          Text("Batal")
        }
      }
    )
  }

  // DIALOG: Test All Areas Results
  if (showAllTestResultsDialog) {
    AlertDialog(
      onDismissRequest = { showAllTestResultsDialog = false },
      title = { Text("Hasil Uji Seluruh Area OCR") },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(areas.value) { area ->
            val resultText = allTestResults[area.field] ?: "(Belum diuji)"
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(8.dp)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "${area.field.fieldName}:",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = getFieldColor(area.field)
                )
                Text(
                  text = resultText,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace,
                  color = if (resultText.startsWith("(")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { showAllTestResultsDialog = false }) {
          Text("Selesai")
        }
      }
    )
  }

  // DIALOG: Confirm Exit if Unsaved
  if (showUnsavedChangesDialog) {
    AlertDialog(
      onDismissRequest = { showUnsavedChangesDialog = false },
      title = { Text("Keluar dari Editor?") },
      text = { Text("Perubahan area OCR yang belum disimpan akan hilang jika Anda keluar sekarang.") },
      confirmButton = {
        TextButton(
          onClick = {
            showUnsavedChangesDialog = false
            onBack()
          }
        ) {
          Text("Keluar", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnsavedChangesDialog = false }) {
          Text("Lanjut Edit")
        }
      }
    )
  }
}

private fun getFieldColor(field: TargetRecipientField): Color {
  return when (field) {
    TargetRecipientField.WAKTU -> Color(0xFF2563EB) // Blue
    TargetRecipientField.BANK -> Color(0xFF0891B2) // Cyan / Teal
    TargetRecipientField.REKENING -> Color(0xFFD97706) // Amber
    TargetRecipientField.PENERIMA -> Color(0xFF7C3AED) // Purple
    TargetRecipientField.NOMINAL -> Color(0xFF16A34A) // Green
    TargetRecipientField.ADMIN -> Color(0xFFE11D48) // Rose
    TargetRecipientField.TOTAL -> Color(0xFF059669) // Emerald
  }
}
