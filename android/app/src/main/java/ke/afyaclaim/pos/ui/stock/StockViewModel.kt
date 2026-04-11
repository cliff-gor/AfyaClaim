package ke.afyaclaim.pos.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.data.repository.InventoryRepository
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.StockReason
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StockFilter(val label: String) { ALL("All"), LOW("Low & out"), SUBSIDISED("Subsidised") }

data class StockUiState(
    val items: List<StockItem> = emptyList(),
    val filter: StockFilter = StockFilter.ALL,
    val lowCount: Int = 0,
    val outCount: Int = 0,
    val totalUnits: Int = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class StockViewModel @Inject constructor(
    private val inventory: InventoryRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(StockFilter.ALL)
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val state: StateFlow<StockUiState> = combine(inventory.stock(), filter) { items, f ->
        StockUiState(
            items = when (f) {
                StockFilter.ALL -> items
                StockFilter.LOW -> items.filter { it.isLow || it.isOut }
                StockFilter.SUBSIDISED -> items.filter { it.product.subsidised }
            }.sortedWith(compareByDescending<StockItem> { it.isOut }.thenByDescending { it.isLow }.thenBy { it.product.name }),
            filter = f,
            lowCount = items.count { it.isLow },
            outCount = items.count { it.isOut },
            totalUnits = items.sumOf { it.quantityOnHand.coerceAtLeast(0) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun setFilter(f: StockFilter) {
        filter.value = f
    }

    fun adjust(item: StockItem, delta: Int, reason: StockReason) {
        if (delta == 0) return
        viewModelScope.launch {
            inventory.adjust(item.product.id, delta, reason)
            val sign = if (delta > 0) "+" else ""
            events.send("${item.product.name}: $sign$delta (${reason.label.lowercase()}) queued for sync")
        }
    }
}
