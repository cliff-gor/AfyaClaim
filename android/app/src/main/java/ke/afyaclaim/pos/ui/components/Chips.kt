package ke.afyaclaim.pos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ke.afyaclaim.pos.domain.model.ClaimStatus
import ke.afyaclaim.pos.domain.model.SyncState
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes

/** Fully rounded status pill. Status colour is never alone: always an icon plus a label. */
@Composable
fun Pill(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(AfyaShapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
fun ClaimStatusPill(status: ClaimStatus, modifier: Modifier = Modifier) {
    val c = Afya.colors
    when (status) {
        ClaimStatus.SUBMITTED -> Pill(status.label, c.lavender, c.ink, modifier, Icons.Rounded.Send)
        ClaimStatus.UNDER_REVIEW -> Pill(status.label, c.amberSoft, if (c.isDark) c.amber else Color(0xFF7A5200), modifier, Icons.Rounded.HourglassTop)
        ClaimStatus.APPROVED -> Pill(status.label, c.sageSoft, c.moss, modifier, Icons.Rounded.CheckCircle)
        ClaimStatus.REJECTED -> Pill(status.label, c.coralSoft, if (c.isDark) c.coral else Color(0xFFB3261E), modifier, Icons.Rounded.Cancel)
        ClaimStatus.PAID -> Pill(status.label, c.lime, c.onLime, modifier, Icons.Rounded.Payments)
    }
}

@Composable
fun SyncStatePill(state: SyncState, modifier: Modifier = Modifier) {
    val c = Afya.colors
    when (state) {
        SyncState.PENDING -> Pill("Pending", c.lavender, c.ink, modifier, Icons.Rounded.CloudUpload)
        SyncState.SYNCED -> Pill("Synced", c.sageSoft, c.moss, modifier, Icons.Rounded.CloudDone)
        SyncState.CONFLICT -> Pill("Conflict", c.amberSoft, if (c.isDark) c.amber else Color(0xFF7A5200), modifier, Icons.Rounded.ReportProblem)
        SyncState.REJECTED -> Pill("Rejected", c.coralSoft, if (c.isDark) c.coral else Color(0xFFB3261E), modifier, Icons.Rounded.Cancel)
    }
}

/** Selectable pill used for filters and single-choice fields (age band, sex, test result). */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedContainer: Color = Afya.colors.ink,
    selectedContent: Color = Afya.colors.bg,
) {
    val c = Afya.colors
    val container by animateColorAsState(if (selected) selectedContainer else c.surface, tween(180), label = "pill-bg")
    val content by animateColorAsState(if (selected) selectedContent else c.ink, tween(180), label = "pill-fg")
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(AfyaShapes.pill)
            .background(container)
            .border(1.dp, if (selected) Color.Transparent else c.outline, AfyaShapes.pill)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
