package ke.afyaclaim.pos.ui.today

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.core.Money
import ke.afyaclaim.pos.core.TimeFormat
import ke.afyaclaim.pos.data.sync.SyncActivity
import ke.afyaclaim.pos.domain.model.SaleSummary
import ke.afyaclaim.pos.domain.model.Session
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.EmptyState
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.IconBadge
import ke.afyaclaim.pos.ui.components.LimeButton
import ke.afyaclaim.pos.ui.components.Pill
import ke.afyaclaim.pos.ui.components.SectionHeader
import ke.afyaclaim.pos.ui.components.SyncStatePill
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun TodayScreen(
    session: Session,
    onNewSale: () -> Unit,
    onStock: () -> Unit,
    onClaims: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Afya.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Greeting(session, onSignOut) }
        item { TotalHero(state, onSync) }
        item { QuickActions(onNewSale, onStock, onClaims, onSync = { viewModel.syncNow(); onSync() }) }
        item {
            SectionHeader("Recent sales") {
                Text("${state.salesCount} today", style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
        if (state.recent.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ShoppingBag,
                    title = "No sales yet today",
                    body = "Record your first sale. It saves instantly, even without network.",
                    action = { LimeButton("New sale", onNewSale, icon = Icons.Rounded.AddShoppingCart) },
                )
            }
        } else {
            items(state.recent, key = { it.clientUuid }) { SaleRow(it) }
        }
    }
}

@Composable
private fun Greeting(session: Session, onSignOut: () -> Unit) {
    val c = Afya.colors
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(TimeFormat.today(), style = MaterialTheme.typography.labelMedium, color = c.muted)
            Text(
                "Habari, ${session.firstName}",
                style = MaterialTheme.typography.headlineLarge,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(session.pharmacyName, session.pharmacyTown).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
        }
        Box {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier.size(52.dp).clip(CircleShape).background(c.sage),
            ) {
                Text(
                    session.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (c.isDark) c.ink else c.moss,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    leadingIcon = { Icon(Icons.Rounded.Logout, contentDescription = null) },
                    onClick = { menu = false; onSignOut() },
                )
            }
        }
    }
}

@Composable
private fun TotalHero(state: TodayUiState, onSync: () -> Unit) {
    val c = Afya.colors
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Today's sales", color = c.onHeroMuted, modifier = Modifier.weight(1f))
            val pending = state.counts.pending
            val attention = state.counts.needsAttention
            Box(
                Modifier.clip(AfyaShapes.pill).heightIn(min = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    attention > 0 -> Pill("$attention need attention", c.coral, Color(0xFF2B0B0A), icon = Icons.Rounded.Warning)
                    pending > 0 -> Pill("$pending pending sync", c.lavender, Color(0xFF0E0F0C), icon = Icons.Rounded.CloudUpload)
                    else -> Pill("All synced", c.onHero.copy(alpha = 0.10f), c.lime, icon = Icons.Rounded.CloudDone)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("KES", style = MaterialTheme.typography.titleMedium, color = c.onHeroMuted)
        Text(Money.format(state.totalCents, withCurrency = false), style = AfyaText.kpi, color = c.lime, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HeroStat("Sales", state.salesCount.toString(), Modifier.weight(1f))
            HeroStat("Claims", state.claimsCount.toString(), Modifier.weight(1f))
            HeroStat(
                "Last sync",
                when (state.activity) {
                    SyncActivity.RUNNING -> "Syncing"
                    else -> state.lastSyncAt?.let { TimeFormat.clock(it) } ?: "Never"
                },
                Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Afya.colors
    Column(
        modifier
            .clip(AfyaShapes.cardSmall)
            .background(c.onHero.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
        Text(value, style = AfyaText.amountLarge, color = c.onHero, maxLines = 1)
    }
}

@Composable
private fun QuickActions(onNewSale: () -> Unit, onStock: () -> Unit, onClaims: () -> Unit, onSync: () -> Unit) {
    val c = Afya.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickAction("New sale", Icons.Rounded.AddShoppingCart, c.lime, c.onLime, c.onLime.copy(alpha = 0.12f), Modifier.weight(1f), onNewSale)
        QuickAction("Stock", Icons.Rounded.Inventory2, c.surface, c.ink, c.sageSoft, Modifier.weight(1f), onStock)
        QuickAction("Claims", Icons.Rounded.ReceiptLong, c.surface, c.ink, c.lavender, Modifier.weight(1f), onClaims)
        QuickAction("Sync", Icons.Rounded.Sync, c.surface, c.ink, c.limeSoft, Modifier.weight(1f), onSync)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    badge: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    AfyaCard(
        modifier = modifier,
        color = container,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconBadge(icon, badge, content, size = 42)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SaleRow(sale: SaleSummary) {
    val c = Afya.colors
    AfyaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.ShoppingBag, c.bg, c.ink, size = 44)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    sale.itemsLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(TimeFormat.clock(sale.soldAt))
                        append(" · ${sale.itemCount} item${if (sale.itemCount == 1) "" else "s"}")
                        if (sale.claimCount > 0) append(" · ${sale.claimCount} claim${if (sale.claimCount == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(sale.totalCents), style = AfyaText.amount, color = c.ink)
                Spacer(Modifier.height(4.dp))
                SyncStatePill(sale.syncState)
            }
        }
    }
}
