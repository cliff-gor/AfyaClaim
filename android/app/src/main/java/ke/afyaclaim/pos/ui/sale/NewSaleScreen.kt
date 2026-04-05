package ke.afyaclaim.pos.ui.sale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Female
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Male
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.core.Money
import ke.afyaclaim.pos.domain.model.AgeBand
import ke.afyaclaim.pos.domain.model.Sex
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.TestResult
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.AfyaTextField
import ke.afyaclaim.pos.ui.components.ChoicePill
import ke.afyaclaim.pos.ui.components.EmptyState
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.LimeButton
import ke.afyaclaim.pos.ui.components.Pill
import ke.afyaclaim.pos.ui.components.QuantityStepper
import ke.afyaclaim.pos.ui.components.ScreenTitle
import ke.afyaclaim.pos.ui.components.SectionHeader
import ke.afyaclaim.pos.ui.navigation.LocalSnackbar
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun NewSaleScreen(onDone: () -> Unit, viewModel: NewSaleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val c = Afya.colors

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenTitle(
                    "New sale",
                    subtitle = "Saved on this phone instantly · syncs in the background",
                    trailing = if (state.itemCount > 0) {
                        {
                            IconButton(onClick = viewModel::clear) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear sale", tint = c.ink)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item {
                AfyaTextField(
                    value = state.form.query,
                    onValueChange = viewModel::setQuery,
                    label = "Search products",
                    leadingIcon = Icons.Rounded.Search,
                )
            }
            if (state.categories.size > 1) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoicePill("All", state.form.category == null, { viewModel.setCategory(null) })
                        state.categories.forEach { cat ->
                            ChoicePill(cat.label, state.form.category == cat, { viewModel.setCategory(cat) })
                        }
                    }
                }
            }
            if (state.loaded && state.catalogue.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Inventory2,
                        title = "No products yet",
                        body = "The catalogue downloads on first sync. Connect to the internet and open Sync.",
                    )
                }
            }
            items(state.visible, key = { it.product.id }) { item ->
                ProductRow(
                    item = item,
                    quantity = state.form.quantities[item.product.id] ?: 0,
                    onQuantity = { viewModel.setQuantity(item.product.id, it) },
                )
            }
            if (state.itemCount > 0) {
                item { SectionHeader("Patient") }
                item { PatientCard(state, viewModel) }
                if (state.draft.hasMalariaTest) {
                    item { TestResultCard(state.form.testResult, viewModel::setTestResult) }
                }
                if (state.draft.subsidisedLines.isNotEmpty()) {
                    item { ClaimToggleCard(state, viewModel::setAttachClaim) }
                }
            }
        }
        CheckoutBar(state, onSave = viewModel::save)
    }
}

@Composable
private fun ProductRow(item: StockItem, quantity: Int, onQuantity: (Int) -> Unit) {
    val c = Afya.colors
    val p = item.product
    val selected = quantity > 0
    AfyaCard(
        contentPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        modifier = if (selected) Modifier.border(2.dp, c.lime, AfyaShapes.card) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.format(p.unitPriceCents)} · ${item.quantityOnHand} in stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isOut) c.coral else c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (p.subsidised && p.subsidyCents > 0) {
                    Pill(
                        "Subsidy ${Money.format(p.subsidyCents)}",
                        c.lavender,
                        c.ink,
                        modifier = Modifier.padding(top = 8.dp),
                        icon = Icons.Rounded.Verified,
                    )
                }
            }
            QuantityStepper(
                value = quantity,
                onChange = onQuantity,
                itemName = p.name,
                max = item.quantityOnHand.coerceAtLeast(quantity),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientCard(state: NewSaleUiState, viewModel: NewSaleViewModel) {
    val c = Afya.colors
    AfyaCard {
        Eyebrow("Age band")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            AgeBand.entries.forEach { band ->
                ChoicePill(band.wire, state.form.ageBand == band, { viewModel.setAgeBand(band) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Eyebrow("Sex")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            Sex.entries.forEach { sex ->
                ChoicePill(
                    sex.label,
                    state.form.sex == sex,
                    { viewModel.setSex(sex) },
                    icon = if (sex == Sex.FEMALE) Icons.Rounded.Female else Icons.Rounded.Male,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AfyaTextField(
            value = state.form.phone,
            onValueChange = viewModel::setPhone,
            label = "Phone (optional)",
            placeholder = "07XX XXX XXX",
            leadingIcon = Icons.Rounded.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().clip(AfyaShapes.input).background(c.sageSoft).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.moss)
            Text(
                "Hashed on this phone into an anonymous ID. The number itself is never stored or sent.",
                style = MaterialTheme.typography.bodySmall,
                color = c.ink,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun TestResultCard(result: TestResult?, onSelect: (TestResult) -> Unit) {
    val c = Afya.colors
    AfyaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Biotech, contentDescription = null, tint = c.ink)
            Text(
                "Malaria test result",
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            ChoicePill(
                TestResult.POSITIVE.label,
                result == TestResult.POSITIVE,
                { onSelect(TestResult.POSITIVE) },
                icon = Icons.Rounded.ErrorOutline,
                selectedContainer = c.coral,
                selectedContent = Color(0xFF2B0B0A),
                modifier = Modifier.weight(1f),
            )
            ChoicePill(
                TestResult.NEGATIVE.label,
                result == TestResult.NEGATIVE,
                { onSelect(TestResult.NEGATIVE) },
                icon = Icons.Rounded.CheckCircle,
                selectedContainer = c.sage,
                selectedContent = Color(0xFF0E0F0C),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClaimToggleCard(state: NewSaleUiState, onToggle: (Boolean) -> Unit) {
    val c = Afya.colors
    val on = state.form.attachClaim
    AfyaCard(color = if (on) c.limeSoft else c.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Attach subsidy claim", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(
                    "${state.draft.subsidisedLines.size} subsidised line${if (state.draft.subsidisedLines.size == 1) "" else "s"} · ${Money.format(state.draft.claimableCents)} claimable",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "Attach subsidy claim" },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.moss,
                    checkedThumbColor = c.lime,
                    uncheckedTrackColor = c.bg,
                    uncheckedBorderColor = c.outline,
                ),
            )
        }
        AnimatedVisibility(visible = on, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.draft.subsidisedLines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${line.quantity} × ${line.product.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(Money.format(line.claimableCents), style = AfyaText.amount, color = c.moss)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutBar(state: NewSaleUiState, onSave: () -> Unit) {
    val c = Afya.colors
    AnimatedVisibility(visible = state.itemCount > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        HeroCard(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
        ) {
            state.form.problem?.let {
                Row(
                    Modifier.fillMaxWidth().clip(AfyaShapes.input).background(c.coral.copy(alpha = 0.18f)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.coral)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.onHero, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.itemCount} item${if (state.itemCount == 1) "" else "s"}" +
                            if (state.claimCount > 0) " · ${state.claimCount} claim${if (state.claimCount == 1) "" else "s"}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onHeroMuted,
                    )
                    Text(Money.format(state.draft.totalCents), style = AfyaText.amountLarge, color = c.lime)
                }
                Spacer(Modifier.width(12.dp))
                LimeButton("Save sale", onSave, icon = Icons.Rounded.CheckCircle, loading = state.form.saving)
            }
        }
    }
}
