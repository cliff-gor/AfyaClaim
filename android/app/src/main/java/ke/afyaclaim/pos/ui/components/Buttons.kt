package ke.afyaclaim.pos.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes

/** Lime primary CTA with moss text, fully rounded. */
@Composable
fun LimeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = Afya.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 56.dp),
        shape = AfyaShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.lime,
            contentColor = c.onLime,
            disabledContainerColor = c.lime.copy(alpha = 0.45f),
            disabledContentColor = c.onLime.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
            label = "lime-button",
        ) { busy ->
            if (busy) {
                CircularProgressIndicator(color = c.onLime, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                ButtonContent(text, icon)
            }
        }
    }
}

/** Ink (or surface on dark hero) secondary CTA. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Afya.colors.ink,
    content: Color = Afya.colors.bg,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = AfyaShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Afya.colors.ink,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = AfyaShapes.pill,
        border = BorderStroke(1.dp, Afya.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
