package ke.afyaclaim.pos.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.afyaclaim.pos.BuildConfig
import ke.afyaclaim.pos.ui.components.AfyaCard
import ke.afyaclaim.pos.ui.components.AfyaTextField
import ke.afyaclaim.pos.ui.components.Eyebrow
import ke.afyaclaim.pos.ui.components.HeroCard
import ke.afyaclaim.pos.ui.components.LimeButton
import ke.afyaclaim.pos.ui.components.Pill
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.theme.AfyaText

@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Afya.colors
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.lime),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MedicalServices, contentDescription = null, tint = c.onLime) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("AfyaClaim", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text("Pharmacy POS", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        HeroCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            Eyebrow("Subsidy payouts within", color = c.onHeroMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("48", style = AfyaText.kpi.copy(fontSize = AfyaText.kpi.fontSize * 1.5f), color = c.lime)
                Text(
                    "hours",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.onHero,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                )
            }
            Text(
                "Record a sale, attach the claim, get paid on M-Pesa. Works offline, syncs when you're back.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onHeroMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                Pill("Offline-first", c.onHero.copy(alpha = 0.10f), c.onHero, icon = Icons.Rounded.CloudOff)
                Pill("M-Pesa", c.lime, c.onLime, icon = Icons.Rounded.Payments)
            }
        }

        AfyaCard {
            Text("Karibu tena", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text(
                "Sign in with your pharmacy account.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            AfyaTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = "Email",
                leadingIcon = Icons.Rounded.AlternateEmail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            AfyaTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = "Password",
                leadingIcon = Icons.Rounded.Lock,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = c.muted,
                        )
                    }
                },
            )
            AnimatedVisibility(visible = state.error != null) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(AfyaShapes.input)
                        .background(c.coralSoft)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.coral)
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            LimeButton(
                text = "Sign in",
                onClick = viewModel::submit,
                icon = Icons.Rounded.ArrowForward,
                enabled = state.canSubmit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            if (BuildConfig.DEBUG) {
                TextButton(
                    onClick = viewModel::fillDemo,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                ) { Text("Use demo pharmacist", color = c.moss) }
            }
        }
        Text(
            "Patient phone numbers never leave this device. Only an anonymous pseudonym is shared.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
