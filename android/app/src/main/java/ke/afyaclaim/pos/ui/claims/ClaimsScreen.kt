package ke.afyaclaim.pos.ui.claims

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.core.Money
import ke.afyaclaim.pos.core.TimeFormat
import ke.afyaclaim.pos.domain.model.ClaimCard
import ke.afyaclaim.pos.domain.model.ClaimStatus
import ke.afyaclaim.pos.domain.model.SyncState
import ke.afyaclaim.pos.domain.model.TestResult
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.ChoicePill
import ke.afyaclaim.pos.ui.components.ClaimStatusPill
import ke.afyaclaim.pos.ui.components.EmptyState
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.Pill
import ke.afyaclaim.pos.ui.components.ScreenTitle
import ke.afyaclaim.pos.ui.components.SyncStatePill
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun ClaimsScreen(viewModel: ClaimsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Afya.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Claims", subtitle = "Statuses update from reviewers on every sync") }
        item {
            HeroCard {
                Eyebrow("Approved, awaiting payout", color = c.onHeroMuted)
                Text(Money.format(state.approvedCents), style = AfyaText.kpiSmall, color = c.lime, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroFigure("In review", Money.format(state.awaitingCents), Modifier.weight(1f))
                    HeroFigure("Paid", Money.format(state.paidCents), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill("All ${state.total}", state.filter == null, { viewModel.setFilter(null) })
                ClaimStatus.entries.forEach { s ->
                    val n = state.countByStatus[s] ?: 0
                    ChoicePill("${s.label} $n", state.filter == s, { viewModel.setFilter(s) })
                }
            }
        }
        if (state.loaded && state.claims.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = if (state.filter == null) "No claims yet" else "No ${state.filter!!.label.lowercase()} claims",
                    body = "Attach a subsidy claim when you record a sale of a subsidised product.",
                )
            }
        }
        items(state.claims, key = { it.clientUuid }) { ClaimItem(it) }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Afya.colors
    Column(
        modifier.clip(AfyaShapes.cardSmall).background(c.onHero.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
        Text(value, style = AfyaText.amount, color = c.onHero, maxLines = 1)
    }
}

@Composable
private fun ClaimItem(claim: ClaimCard) {
    val c = Afya.colors
    AfyaCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    claim.reference ?: "Awaiting reference",
                    style = AfyaText.amount.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = if (claim.reference != null) c.muted else c.muted.copy(alpha = 0.7f),
                )
                Text(
                    claim.productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "${claim.quantity} × · ${TimeFormat.dayAndTime(claim.submittedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(Money.format(claim.amountCents), style = AfyaText.amount, color = c.ink)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            ClaimStatusPill(claim.status)
            claim.testResult?.let { result ->
                Pill(
                    "RDT ${result.label.lowercase()}",
                    if (result == TestResult.POSITIVE) c.coralSoft else c.bg,
                    c.ink,
                    icon = Icons.Rounded.Biotech,
                )
            }
            if (claim.syncState != SyncState.SYNCED) SyncStatePill(claim.syncState)
        }
        if (claim.status == ClaimStatus.REJECTED && !claim.rejectionReason.isNullOrBlank()) {
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(AfyaShapes.input).background(c.coralSoft).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = if (c.isDark) c.coral else Color(0xFFB3261E))
                Column(Modifier.padding(start = 10.dp)) {
                    Text("Rejection reason", style = MaterialTheme.typography.labelMedium, color = if (c.isDark) c.coral else Color(0xFFB3261E))
                    Text(claim.rejectionReason, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            }
        }
    }
}
