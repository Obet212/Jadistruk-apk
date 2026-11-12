package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ColumnConfig
import com.example.data.model.Template
import com.example.engine.EscPosCharacterNormalizer

@Composable
fun ReceiptPaperView(
    template: Template,
    columns: List<ColumnConfig>,
    columnValues: Map<Long, String>,
    onFieldClick: (ColumnConfig, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val is80mm = template.paperWidthMm == 80
    val maxPaperWidth = if (is80mm) 420.dp else 340.dp
    val sortedColumns = columns.filter { it.isEnabled }.sortedBy { it.orderIndex }

    Card(
        modifier = modifier
            .widthIn(max = maxPaperWidth)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp))
            .testTag("receipt_paper_card"),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAF9F6) // Natural thermal paper off-white
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Receipt Header Banner
            Text(
                text = EscPosCharacterNormalizer.normalizeText(template.headerTitle),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            )

            if (template.headerSubtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = EscPosCharacterNormalizer.normalizeText(template.headerSubtitle),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF64748B),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider Line
            ReceiptDottedDivider(isDouble = true)

            Spacer(modifier = Modifier.height(10.dp))

            // Tap Instruction Hint
            Surface(
                color = Color(0xFFE2E8F0).copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.height(12.dp),
                        tint = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ketuk baris manapun untuk mengedit langsung",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF475569)
                    )
                }
            }

            // Body Columns
            if (sortedColumns.isEmpty()) {
                Text(
                    text = "(Belum ada kolom aktif pada template ini)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in sortedColumns) {
                        val rawValue = columnValues[col.id]
                            ?: if (col.isManual) col.manualDefaultText else ""
                        val displayValue = EscPosCharacterNormalizer.normalizeText(rawValue)

                        ReceiptLineItem(
                            column = col,
                            value = displayValue,
                            onClick = { onFieldClick(col, displayValue) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Divider
            ReceiptDottedDivider(isDouble = false)

            Spacer(modifier = Modifier.height(10.dp))

            // Footer Notes
            if (template.footerNote.isNotBlank()) {
                Text(
                    text = EscPosCharacterNormalizer.normalizeText(template.footerNote),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF475569),
                    lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Jagged Cut Visual
            Text(
                text = "* * * CETAKIN * * *",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReceiptLineItem(
    column: ColumnConfig,
    value: String,
    onClick: () -> Unit
) {
    val isPlaceholder = value.isBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("receipt_line_${column.name.lowercase().replace(" ", "_")}")
    ) {
        if (column.isManual) {
            // MANUAL COLUMN: LEFT ALIGNED
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${column.name}: ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Text(
                    text = if (isPlaceholder) "(Ketuk untuk isi)" else value,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (isPlaceholder) Color(0xFF94A3B8) else Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .height(14.dp)
                        .padding(start = 4.dp)
                )
            }
        } else {
            // AUTO COLUMN: RIGHT ALIGNED (Label left, Value right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = column.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF475569),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPlaceholder) "(Kosong - ketuk)" else value,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = if (isPlaceholder) Color(0xFFDC2626) else Color(0xFF0F172A)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .height(14.dp)
                            .padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDottedDivider(isDouble: Boolean = false) {
    val char = if (isDouble) "=" else "-"
    Text(
        text = char.repeat(34),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = Color(0xFFCBD5E1),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
