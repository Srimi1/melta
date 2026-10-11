package com.melta.app.battery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.melta.app.R
import com.melta.app.battery.creations.Creation
import com.melta.app.battery.creations.CreationKind
import com.melta.app.battery.creations.CreationStore
import com.melta.app.battery.sprites.PokemonEntry
import com.melta.app.battery.sprites.Pokedex
import com.melta.app.battery.sprites.SpriteRepository
import com.melta.app.core.prefs.BatteryBarConfig
import com.melta.app.core.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonPickerScreen(onBack: () -> Unit, onCreate: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val scope = rememberCoroutineScope()
    val config by store.batteryBar.collectAsStateWithLifecycle(initialValue = BatteryBarConfig())

    var all by remember { mutableStateOf(emptyList<PokemonEntry>()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { Pokedex.all(context) }
    }
    var refresh by remember { mutableIntStateOf(0) }
    var creations by remember { mutableStateOf(emptyList<Creation>()) }
    LaunchedEffect(refresh) {
        creations = CreationStore.list(context).filter { it.kind == CreationKind.SPRITE }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var showShiny by rememberSaveable { mutableStateOf(false) }
    val popular = remember(all) { POPULAR.mapNotNull { id -> all.firstOrNull { it.id == id } } }
    val shown = remember(all, query, type) { Pokedex.search(all, query, type) }

    fun choosePokemon(id: Int) {
        scope.launch { store.updateBatteryBar { it.copy(pokemonId = id, useCustomSprite = false) } }
        onBack()
    }

    fun chooseCreation(c: Creation) {
        scope.launch {
            store.updateBatteryBar { it.copy(useCustomSprite = true, customSpritePath = c.file.absolutePath) }
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pick_companion)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconToggleButton(checked = showShiny, onCheckedChange = { showShiny = it }) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.show_shiny),
                            tint = if (showShiny) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        stringResource(R.string.my_creations),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable(onClick = onCreate),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_pattern))
                            }
                        }
                        items(creations, key = { it.file.absolutePath }) { c ->
                            Box {
                                AsyncImage(
                                    model = c.file,
                                    contentDescription = null,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { chooseCreation(c) }
                                        .padding(8.dp),
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            CreationStore.delete(c)
                                            refresh++
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    if (popular.isNotEmpty()) {
                        Text(
                            stringResource(R.string.popular),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(popular, key = { "popular_${it.id}" }) { p ->
                                PokemonCell(
                                    p,
                                    shiny = showShiny,
                                    selected = !config.useCustomSprite && config.pokemonId == p.id,
                                    onClick = { choosePokemon(p.id) },
                                    modifier = Modifier.width(88.dp),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        item {
                            FilterChip(selected = type == null, onClick = { type = null }, label = { Text("All") })
                        }
                        items(Pokedex.allTypes) { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = if (type == t) null else t },
                                label = { Text(t.replaceFirstChar(Char::uppercase)) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.pokemon_count, shown.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(shown, key = { it.id }) { p ->
                PokemonCell(p, shiny = showShiny, selected = !config.useCustomSprite && config.pokemonId == p.id, onClick = { choosePokemon(p.id) })
            }
        }
    }
}

@Composable
private fun PokemonCell(
    p: PokemonEntry,
    shiny: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = SpriteRepository.thumbnail(context, p.id, shiny),
            contentDescription = p.name,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row {
            Text(p.number, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fan favourites shown first. Pikachu always leads. */
private val POPULAR = listOf(25, 133, 4, 1, 7, 94, 143, 448, 151, 39)
