package ke.afyaclaim.pos.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RemoveShoppingCart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.ChoicePill
import ke.afyaclaim.pos.ui.components.EmptyState
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.Pill
import ke.afyaclaim.pos.ui.components.ScreenTitle
import ke.afyaclaim.pos.ui.navigation.LocalSnackbar
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun StockScreen(viewModel: StockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val c = Afya.colors
    var adjusting by remember { mutableStateOf<StockItem?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Stock", subtitle = "Server count wins after each sync") }
        item {
            HeroCard {
                Row {
                    HeroMetric("Units on hand", state.totalUnits.toString(), c.lime, Modifier.weight(1f))
                    HeroMetric("Low", state.lowCount.toString(), c.amber, Modifier.weight(0.6f))
                    HeroMetric("Out", state.outCount.toString(), c.coral, Modifier.weight(0.6f))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StockFilter.entries.forEach { f -> ChoicePill(f.label, state.filter == f, { viewModel.setFilter(f) }) }
            }
        }
        if (state.loaded && state.items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Inventory2,
                    title = if (state.filter == StockFilter.LOW) "Nothing running low" else "No stock yet",
                    body = if (state.filter == StockFilter.LOW) "Every product is above its reorder level." else "Inventory downloads on first sync.",
                )
            }
        }
        items(state.items, key = { it.product.id }) { item ->
            StockCard(item, onAdjust = { adjusting = item })
        }
    }

    adjusting?.let { item ->
        AdjustStockSheet(
            item = item,
            onDismiss = { adjusting = null },
            onConfirm = { delta, reason ->
                viewModel.adjust(item, delta, reason)
                adjusting = null
            },
        )
    }
}

@Composable
private fun HeroMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val c = Afya.colors
    Column(modifier) {
        Eyebrow(label, color = c.onHeroMuted)
        Text(value, style = AfyaText.kpiSmall, color = accent, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StockCard(item: StockItem, onAdjust: () -> Unit) {
    val c = Afya.colors
    val level = when {
        item.isOut -> c.coral
        item.isLow -> c.amber
        else -> c.moss
    }
    AfyaCard(contentPadding = PaddingValues(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.product.category.label} · reorder at ${item.reorderLevel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                    when {
                        item.isOut -> Pill("Out of stock", c.coralSoft, if (c.isDark) c.coral else Color(0xFFB3261E), icon = Icons.Rounded.RemoveShoppingCart)
                        item.isLow -> Pill("Low stock", c.amberSoft, if (c.isDark) c.amber else Color(0xFF7A5200), icon = Icons.Rounded.TrendingDown)
                        else -> Pill("In stock", c.sageSoft, c.moss, icon = Icons.Rounded.CheckCircle)
                    }
                    if (item.pendingAdjustments > 0) {
                        Pill("${item.pendingAdjustments} pending", c.lavender, c.ink, icon = Icons.Rounded.CloudUpload)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(item.quantityOnHand.toString(), style = AfyaText.amountLarge, color = c.ink)
                LevelBar(item, level)
            }
            FilledTonalIconButton(
                onClick = onAdjust,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.bg, contentColor = c.ink),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Adjust stock for ${item.product.name}")
            }
        }
    }
}

/** Five-segment gauge relative to three times the reorder level. */
@Composable
private fun LevelBar(item: StockItem, color: Color) {
    val c = Afya.colors
    val capacity = (item.reorderLevel * 3).coerceAtLeast(1)
    val filled = ((item.quantityOnHand.toFloat() / capacity) * 5).toInt()
        .coerceIn(if (item.quantityOnHand > 0) 1 else 0, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 6.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(width = 8.dp, height = 6.dp)
                    .clip(AfyaShapes.pill)
                    .background(if (i < filled) color else c.outline),
            )
        }
    }
}
