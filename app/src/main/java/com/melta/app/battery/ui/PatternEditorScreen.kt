package com.melta.app.battery.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.melta.app.R
import com.melta.app.battery.creations.CreationKind
import com.melta.app.battery.creations.CreationStore
import com.melta.app.battery.creations.PixelGrid
import com.melta.app.battery.sprites.Mascot
import com.melta.app.core.prefs.FillStyle
import com.melta.app.core.prefs.SettingsStore
import kotlinx.coroutines.launch

private enum class Tool { PEN, ERASER, FILL }

private val Palette = listOf(
    0xFF000000, 0xFF2B1B3D, 0xFF5B2A86, 0xFF7C4DFF, 0xFF448AFF, 0xFF40C4FF,
    0xFF26A69A, 0xFF66BB6A, 0xFFB2FF59, 0xFFFFEB3B, 0xFFFFC93C, 0xFFFF9800,
    0xFFFF5722, 0xFFFF5252, 0xFFFF80AB, 0xFFF48FB1, 0xFF8D6E63, 0xFF5D4037,
    0xFF9E9E9E, 0xFFE0E0E0, 0xFFFFFFFF, 0xFFFFF4C2, 0xFF81D4FA, 0xFFE1F5FE,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatternEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore.get(context) }

    var gridSize by remember { mutableIntStateOf(16) }
    val grid = remember(gridSize) { PixelGrid(gridSize) }
    var tick by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableIntStateOf(Palette[10]) }
    var mirror by remember { mutableStateOf(false) }

    fun refresh() {
        tick = grid.version
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val pixels = CreationStore.importToGrid(context, uri, grid.size)
                if (pixels != null) {
                    grid.checkpoint()
                    grid.load(pixels)
                    refresh()
                }
            }
        }
    }

    fun save(kind: CreationKind) {
        if (grid.isEmpty) {
            Toast.makeText(context, R.string.editor_empty, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val file = CreationStore.save(context, grid, kind)
            store.updateBatteryBar {
                when (kind) {
                    CreationKind.SPRITE -> it.copy(useCustomSprite = true, customSpritePath = file.absolutePath)
                    CreationKind.TILE -> it.copy(fillStyle = FillStyle.CUSTOM, customTilePath = file.absolutePath)
                }
            }
            Toast.makeText(context, R.string.editor_saved, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_pattern)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { grid.undo(); refresh() }, enabled = grid.canUndo && tick >= 0) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.undo))
                    }
                    IconButton(onClick = { grid.checkpoint(); grid.clear(); refresh() }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(16, 24, 32).forEach { s ->
                    FilterChip(selected = gridSize == s, onClick = { gridSize = s; tick = 0 }, label = { Text("${s}x$s") })
                }
                Spacer(Modifier.weight(1f))
                FilterChip(selected = mirror, onClick = { mirror = !mirror }, label = { Text(stringResource(R.string.mirror)) })
            }

            PixelCanvas(grid, tick, onStroke = { x, y, first ->
                if (first) grid.checkpoint()
                when (tool) {
                    Tool.PEN -> grid.paint(x, y, color, mirror)
                    Tool.ERASER -> grid.paint(x, y, 0, mirror)
                    Tool.FILL -> if (first) grid.fill(x, y, color)
                }
                refresh()
            })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip(Tool.PEN, tool, Icons.Filled.Edit, stringResource(R.string.tool_pen)) { tool = it }
                ToolChip(Tool.ERASER, tool, Icons.Filled.AutoFixNormal, stringResource(R.string.tool_eraser)) { tool = it }
                ToolChip(Tool.FILL, tool, Icons.Filled.FormatColorFill, stringResource(R.string.tool_fill)) { tool = it }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Palette.forEach { c ->
                    val sel = c == color
                    Spacer(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable {
                                color = c
                                if (tool == Tool.ERASER) tool = Tool.PEN
                            },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.import_image))
                }
                if (gridSize == 16) {
                    FilledTonalButton(onClick = { grid.checkpoint(); grid.load(Mascot.pixels()); refresh() }) {
                        Text(stringResource(R.string.start_from_melty))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = { save(CreationKind.SPRITE) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_as_companion))
            }
            FilledTonalButton(onClick = { save(CreationKind.TILE) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_as_pattern))
            }
            Text(
                stringResource(R.string.editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolChip(tool: Tool, current: Tool, icon: ImageVector, label: String, onSelect: (Tool) -> Unit) {
    FilterChip(
        selected = tool == current,
        onClick = { onSelect(tool) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/** Draws the grid with a checkerboard behind transparent cells and handles tap and drag painting. */
@Composable
private fun PixelCanvas(grid: PixelGrid, tick: Int, onStroke: (x: Int, y: Int, first: Boolean) -> Unit) {
    val light = Color(0xFFEDEDED)
    val dark = Color(0xFFD6D6D6)
    val line = Color(0x22000000)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(grid) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cell = size.width.toFloat() / grid.size
                    var lastX = -1
                    var lastY = -1
                    fun hit(p: Offset, first: Boolean) {
                        val x = (p.x / cell).toInt()
                        val y = (p.y / cell).toInt()
                        if (!grid.inBounds(x, y) || (x == lastX && y == lastY)) return
                        lastX = x
                        lastY = y
                        onStroke(x, y, first)
                    }
                    hit(down.position, true)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        hit(change.position, false)
                        change.consume()
                    }
                }
            },
    ) {
        if (tick < 0) return@Canvas // reading tick makes the canvas redraw after every edit
        val n = grid.size
        val cell = size.width / n
        val cellSize = Size(cell, cell)
        for (y in 0 until n) for (x in 0 until n) {
            val tl = Offset(x * cell, y * cell)
            val c = grid[x, y]
            if (c ushr 24 == 0) {
                drawRect(if ((x + y) % 2 == 0) light else dark, tl, cellSize)
            } else {
                drawRect(Color(c), tl, cellSize)
            }
        }
        for (i in 0..n) {
            val p = i * cell
            drawLine(line, Offset(p, 0f), Offset(p, size.height))
            drawLine(line, Offset(0f, p), Offset(size.width, p))
        }
    }
}
