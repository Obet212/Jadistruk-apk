package com.example.ui.screens.templates

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AreaConfigHelper
import com.example.data.model.OcrTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
  templates: List<OcrTemplate>,
  onOpenTemplateEditor: (template: OcrTemplate?, sampleImageUri: Uri?) -> Unit,
  onSaveTemplate: (OcrTemplate) -> Unit,
  onDeleteTemplate: (OcrTemplate) -> Unit,
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Semua") }
  var templateToDelete by remember { mutableStateOf<OcrTemplate?>(null) }
  var showCreateOptionsDialog by remember { mutableStateOf(false) }

  val categories = listOf("Semua", "Bank Transfer", "Bank Digital", "E-Wallet")

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        onOpenTemplateEditor(null, uri)
      }
    }
  )

  val filteredTemplates = templates.filter { tpl ->
    val matchesSearch = tpl.bankName.contains(searchQuery, ignoreCase = true) ||
      tpl.detectionKeywords.contains(searchQuery, ignoreCase = true)
    val matchesCategory = selectedCategory == "Semua" || tpl.category.equals(selectedCategory, ignoreCase = true)
    matchesSearch && matchesCategory
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Template OCR",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Pola & Area OCR Penerima Layar Penuh",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showCreateOptionsDialog = true },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Editor Template Baru") },
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("fab_add_template")
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Search and Category Filter
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Cari nama bank atau kata kunci...") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("search_template_input"),
          singleLine = true,
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categories) { cat ->
            val isSelected = selectedCategory == cat
            AssistChip(
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
              leadingIcon = if (isSelected) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
              } else null,
              colors = AssistChipDefaults.assistChipColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
              )
            )
          }
        }
      }

      // Template list
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (filteredTemplates.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Tidak ada template yang cocok",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        } else {
          items(filteredTemplates) { template ->
            TemplateItemCard(
              template = template,
              onOpenEditor = {
                val sampleUri = template.sampleImageUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                onOpenTemplateEditor(template, sampleUri)
              },
              onDelete = {
                templateToDelete = template
              }
            )
          }
        }
      }
    }
  }

  // Create Options Dialog
  if (showCreateOptionsDialog) {
    AlertDialog(
      onDismissRequest = { showCreateOptionsDialog = false },
      title = { Text("Buat Template OCR") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Pilih screenshot bukti transfer untuk dipetakan secara visual di layar penuh:",
            style = MaterialTheme.typography.bodyMedium
          )

          Spacer(modifier = Modifier.height(4.dp))

          Button(
            onClick = {
              showCreateOptionsDialog = false
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih Screenshot dari Galeri")
          }

          OutlinedButton(
            onClick = {
              showCreateOptionsDialog = false
              onOpenTemplateEditor(null, null)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Filled.CropFree, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buka Editor Tanpa Gambar Awal")
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showCreateOptionsDialog = false }) {
          Text("Batal")
        }
      }
    )
  }

  // Confirm Delete Dialog
  if (templateToDelete != null) {
    AlertDialog(
      onDismissRequest = { templateToDelete = null },
      title = { Text("Hapus Template?") },
      text = {
        Text("Apakah Anda yakin ingin menghapus template \"${templateToDelete?.bankName}\"? Data area OCR yang tersimpan akan dihapus.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            templateToDelete?.let {
              onDeleteTemplate(it)
              Toast.makeText(context, "Template dihapus", Toast.LENGTH_SHORT).show()
            }
            templateToDelete = null
          }
        ) {
          Text("Hapus", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { templateToDelete = null }) {
          Text("Batal")
        }
      }
    )
  }
}

@Composable
private fun TemplateItemCard(
  template: OcrTemplate,
  onOpenEditor: () -> Unit,
  onDelete: () -> Unit,
) {
  val configuredAreas = remember(template.areaConfigJson) {
    AreaConfigHelper.fromJson(template.areaConfigJson)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("template_card_${template.id}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          if (template.sampleImageUri.isNotBlank()) {
            AsyncImage(
              model = template.sampleImageUri,
              contentDescription = "Screenshot Template",
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp)),
              contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
          } else {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = template.bankName,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
              text = "${template.category} • ${if (template.isBuiltIn) "Preset Bawaan" else "Kustom"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("btn_delete_template_${template.id}")
        ) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = "Hapus Template",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Status Area OCR & Buka Editor Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (configuredAreas.isNotEmpty()) {
          Text(
            text = "✓ ${configuredAreas.size} Area OCR Tersimpan",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
          )
        } else {
          Text(
            text = "Belum ada area koordinat OCR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
          onClick = onOpenEditor,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (configuredAreas.isEmpty()) "Petakan Area" else "Edit Area OCR",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
          )
        }
      }
    }
  }
}
