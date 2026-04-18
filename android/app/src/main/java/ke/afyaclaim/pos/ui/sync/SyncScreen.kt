package ke.afyaclaim.pos.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.core.TimeFormat
import ke.afyaclaim.pos.data.sync.SyncActivity
import ke.afyaclaim.pos.domain.model.RecordType
import ke.afyaclaim.pos.domain.model.SyncRecord
import ke.afyaclaim.pos.domain.model.SyncState
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.EmptyState
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.GhostButton
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.IconBadge
import ke.afyaclaim.pos.ui.components.LimeButton
import ke.afyaclaim.pos.ui.components.ScreenTitle
import ke.afyaclaim.pos.ui.components.SectionHeader
import ke.afyaclaim.pos.ui.components.SyncStatePill
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun SyncScreen(viewModel: SyncViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Afya.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Sync", subtitle = "Every 15 minutes on any network, or now") }
        item { SyncHero(state, onSync = viewModel::syncNow) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountTile("Pending", state.counts.pending, Icons.Rounded.CloudUpload, c.lavender, c.ink, Modifier.weight(1f))
                CountTile("Conflict", state.counts.conflict, Icons.Rounded.ReportProblem, c.amberSoft, c.ink, Modifier.weight(1f))
                CountTile("Rejected", state.counts.rejected, Icons.Rounded.Cancel, c.coralSoft, c.ink, Modifier.weight(1f))
            }
        }
        item { SectionHeader("Waiting on the server") }
        if (state.records.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.CloudDone,
                    title = "Everything is synced",
                    body = "Sales, claims and stock adjustments are all on the server.",
                )
            }
        } else {
            items(state.records, key = { "${it.type.wire}:${it.clientUuid}" }) { record ->
                RecordCard(record, onRetry = { viewModel.retry(record) })
            }
        }
    }
}

@Composable
private fun SyncHero(state: SyncUiState, onSync: () -> Unit) {
    val c = Afya.colors
    val running = state.activity == SyncActivity.RUNNING
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Last synced", color = c.onHeroMuted)
                Text(
                    state.lastSyncAt?.let { TimeFormat.clock(it) } ?: "--:--",
                    style = AfyaText.kpi,
                    color = c.lime,
                )
                Text(
                    when (state.activity) {
                        SyncActivity.RUNNING -> "Syncing now…"
                        SyncActivity.WAITING_FOR_NETWORK -> "Waiting for network"
                        SyncActivity.FAILED -> "Last attempt failed"
                        SyncActivity.IDLE -> state.lastSyncAt?.let { TimeFormat.relative(it) } ?: "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onHeroMuted,
                )
            }
            Icon(
                when (state.activity) {
                    SyncActivity.WAITING_FOR_NETWORK -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.Sync
                },
                contentDescription = null,
                tint = c.lime,
                modifier = Modifier.size(40.dp).rotate(if (running) angle else 0f),
            )
        }
        state.lastError?.let {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(AfyaShapes.input).background(c.coral.copy(alpha = 0.18f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ReportProblem, contentDescription = null, tint = c.coral)
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.onHero, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        LimeButton(
            text = if (running) "Syncing" else "Sync now",
            onClick = onSync,
            icon = Icons.Rounded.Sync,
            loading = running,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CountTile(label: String, count: Int, icon: ImageVector, tint: Color, content: Color, modifier: Modifier) {
    val c = Afya.colors
    AfyaCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        IconBadge(icon, tint, content, size = 36)
        Text(count.toString(), style = AfyaText.kpiSmall, color = c.ink, modifier = Modifier.padding(top = 10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

@Composable
private fun RecordCard(record: SyncRecord, onRetry: () -> Unit) {
    val c = Afya.colors
    val icon = when (record.type) {
        RecordType.SALE -> Icons.Rounded.ShoppingBag
        RecordType.CLAIM -> Icons.Rounded.ReceiptLong
        RecordType.STOCK_ADJUSTMENT -> Icons.Rounded.Inventory2
    }
    AfyaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, c.bg, c.ink, size = 44)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(record.title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${record.subtitle} · ${TimeFormat.dayAndTime(record.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SyncStatePill(record.state)
        }
        if (record.state != SyncState.PENDING) {
            record.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(AfyaShapes.input)
                        .background(if (record.state == SyncState.CONFLICT) c.amberSoft else c.coralSoft).padding(12.dp),
                )
            }
            GhostButton("Retry", onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
