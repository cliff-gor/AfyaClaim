package ke.afyaclaim.pos.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.core.TimeFormat
import ke.afyaclaim.pos.data.repository.SaleRepository
import ke.afyaclaim.pos.data.repository.SyncRepository
import ke.afyaclaim.pos.data.sync.SyncActivity
import ke.afyaclaim.pos.domain.model.SaleSummary
import ke.afyaclaim.pos.domain.model.SyncCounts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TodayUiState(
    val totalCents: Long = 0,
    val salesCount: Int = 0,
    val claimsCount: Int = 0,
    val counts: SyncCounts = SyncCounts(),
    val recent: List<SaleSummary> = emptyList(),
    val lastSyncAt: Long? = null,
    val activity: SyncActivity = SyncActivity.IDLE,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    sales: SaleRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    private val since = TimeFormat.startOfToday()

    private val totals = combine(
        sales.totalSince(since),
        sales.countSince(since),
        sales.claimsSince(since),
    ) { total, count, claims -> Triple(total, count, claims) }

    val state: StateFlow<TodayUiState> = combine(
        totals,
        sync.counts,
        sales.recentSales(6),
        sync.lastSyncAt,
        sync.activity,
    ) { (total, count, claims), counts, recent, lastSync, activity ->
        TodayUiState(total, count, claims, counts, recent, lastSync, activity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun syncNow() = sync.syncNow()
}
