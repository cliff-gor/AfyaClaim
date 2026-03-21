package ke.afyaclaim.pos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

/** White surface card; elevation comes from contrast with the off-white background, not shadow. */
@Composable
fun AfyaCard(
    modifier: Modifier = Modifier,
    color: Color = Afya.colors.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AfyaShapes.card)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Ink hero card for the screen's headline metric. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    AfyaCard(modifier = modifier, color = Afya.colors.hero, contentPadding = contentPadding, content = content)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Afya.colors.muted) {
    Text(text.uppercase(), style = AfyaText.eyebrow, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Afya.colors.ink,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.48f).dp))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    AfyaCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(28.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            IconBadge(icon, Afya.colors.sageSoft, Afya.colors.moss, size = 64)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Afya.colors.ink,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = Afya.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (action != null) {
                Box(Modifier.padding(top = 18.dp)) { action() }
            }
        }
    }
}
