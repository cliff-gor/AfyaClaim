package ke.afyaclaim.pos.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.StockReason
import ke.afyaclaim.pos.ui.components.ChoicePill
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.LimeButton
import ke.afyaclaim.pos.ui.components.QuantityStepper
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaText

/** Collects a signed stock delta and reason. The app only ever sends deltas, never absolute counts. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdjustStockSheet(
    item: StockItem,
    onDismiss: () -> Unit,
    onConfirm: (delta: Int, reason: StockReason) -> Unit,
) {
    val c = Afya.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableStateOf(StockReason.RECEIVED) }
    var amount by rememberSaveable { mutableIntStateOf(1) }
    val delta = if (reason == StockReason.RECEIVED) amount else -amount
    val maxRemoval = item.quantityOnHand.coerceAtLeast(0)
    val resulting = item.quantityOnHand + delta

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        dragHandle = null,
    ) {
        Column(Modifier.padding(24.dp).navigationBarsPadding()) {
            Eyebrow("Adjust stock")
            Text(item.product.name, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))
            Eyebrow("Reason")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                StockReason.entries.forEach { r ->
                    ChoicePill(r.label, reason == r, {
                        reason = r
                        if (r != StockReason.RECEIVED) amount = amount.coerceAtMost(maxRemoval.coerceAtLeast(1))
                    })
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(if (delta >= 0) "Adding" else "Removing", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(
                        (if (delta > 0) "+" else "") + delta,
                        style = AfyaText.kpiSmall,
                        color = if (delta >= 0) c.moss else c.coral,
                    )
                }
                QuantityStepper(
                    value = amount,
                    onChange = { amount = it },
                    itemName = "adjustment",
                    min = 1,
                    max = if (reason == StockReason.RECEIVED) 9_999 else maxRemoval.coerceAtLeast(1),
                )
            }
            Text(
                "On hand ${item.quantityOnHand} → $resulting",
                style = AfyaText.amount,
                color = if (resulting < 0) c.coral else c.muted,
                modifier = Modifier.padding(top = 12.dp),
            )
            LimeButton(
                text = "Save adjustment",
                onClick = { onConfirm(delta, reason) },
                icon = Icons.Rounded.Check,
                enabled = delta != 0 && resulting >= 0,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
    }
}
