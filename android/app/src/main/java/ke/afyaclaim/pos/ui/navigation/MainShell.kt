package ke.afyaclaim.pos.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ke.afyaclaim.pos.domain.model.Session
import ke.afyaclaim.pos.ui.claims.ClaimsScreen
import ke.afyaclaim.pos.ui.sale.NewSaleScreen
import ke.afyaclaim.pos.ui.stock.StockScreen
import ke.afyaclaim.pos.ui.sync.SyncScreen
import ke.afyaclaim.pos.ui.sync.SyncViewModel
import ke.afyaclaim.pos.ui.theme.Afya
import ke.afyaclaim.pos.ui.theme.AfyaShapes
import ke.afyaclaim.pos.ui.today.TodayScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Home),
    SALE("sale", "Sale", Icons.Rounded.PointOfSale),
    STOCK("stock", "Stock", Icons.Rounded.Inventory2),
    CLAIMS("claims", "Claims", Icons.Rounded.ReceiptLong),
    SYNC("sync", "Sync", Icons.Rounded.Sync),
}

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }

@Composable
fun MainShell(session: Session, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val syncViewModel: SyncViewModel = hiltViewModel()
    val syncState by syncViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Tab.TODAY.route

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = Afya.colors.bg,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = AfyaShapes.cardSmall,
                        containerColor = Afya.colors.hero,
                        contentColor = Afya.colors.onHero,
                        actionColor = Afya.colors.lime,
                    )
                }
            },
            bottomBar = {
                AfyaBottomBar(
                    current = currentRoute,
                    attentionCount = syncState.counts.pending + syncState.counts.needsAttention,
                    onSelect = { navController.navigateToTab(it) },
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.TODAY.route,
                modifier = Modifier.fillMaxSize().padding(padding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                composable(Tab.TODAY.route) {
                    TodayScreen(
                        session = session,
                        onNewSale = { navController.navigateToTab(Tab.SALE) },
                        onStock = { navController.navigateToTab(Tab.STOCK) },
                        onClaims = { navController.navigateToTab(Tab.CLAIMS) },
                        onSync = { navController.navigateToTab(Tab.SYNC) },
                        onSignOut = onSignOut,
                    )
                }
                composable(Tab.SALE.route) { NewSaleScreen(onDone = { navController.navigateToTab(Tab.TODAY) }) }
                composable(Tab.STOCK.route) { StockScreen() }
                composable(Tab.CLAIMS.route) { ClaimsScreen() }
                composable(Tab.SYNC.route) { SyncScreen(viewModel = syncViewModel) }
            }
        }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Floating ink capsule with a lime active pill: icon + label on every item. */
@Composable
private fun AfyaBottomBar(current: String, attentionCount: Int, onSelect: (Tab) -> Unit) {
    val c = Afya.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AfyaShapes.card)
                .background(c.hero)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { tab ->
                val selected = current == tab.route
                val bg by animateColorAsState(if (selected) c.lime else c.hero, tween(200), label = "tab-bg")
                val fg by animateColorAsState(if (selected) c.onLime else c.onHeroMuted, tween(200), label = "tab-fg")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(AfyaShapes.cardSmall)
                        .background(bg)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (tab == Tab.SYNC && attentionCount > 0) {
                        BadgedBox(badge = {
                            Badge(containerColor = c.coral, contentColor = c.surface) { Text(attentionCount.coerceAtMost(99).toString()) }
                        }) {
                            Icon(tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
