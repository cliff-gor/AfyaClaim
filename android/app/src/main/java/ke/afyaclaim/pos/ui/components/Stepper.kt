package ke.afyaclaim.pos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun QuantityStepper(
    value: Int,
    onChange: (Int) -> Unit,
    itemName: String,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999,
    showSign: Boolean = false,
) {
    val c = Afya.colors
    Row(
        modifier = modifier.clip(AfyaShapes.pill).background(c.bg).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FilledIconButton(
            onClick = { onChange((value - 1).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.surface,
                contentColor = c.ink,
                disabledContainerColor = c.surface.copy(alpha = 0.5f),
                disabledContentColor = c.muted.copy(alpha = 0.5f),
            ),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease $itemName") }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(180)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(150)) { if (up) -it else it } + fadeOut(tween(150)))
            },
            label = "qty",
        ) { qty ->
            Text(
                text = if (showSign && qty > 0) "+$qty" else qty.toString(),
                style = AfyaText.amount,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
        FilledIconButton(
            onClick = { onChange((value + 1).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.lime,
                contentColor = c.onLime,
                disabledContainerColor = c.lime.copy(alpha = 0.4f),
                disabledContentColor = c.onLime.copy(alpha = 0.5f),
            ),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Increase $itemName") }
    }
}
