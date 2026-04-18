package ke.afyaclaim.pos.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.data.repository.SyncRepository
import ke.afyaclaim.pos.data.sync.SyncActivity
import ke.afyaclaim.pos.domain.model.SyncCounts
import ke.afyaclaim.pos.domain.model.SyncRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val counts: SyncCounts = SyncCounts(),
    val records: List<SyncRecord> = emptyList(),
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val activity: SyncActivity = SyncActivity.IDLE,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repository: SyncRepository,
) : ViewModel() {
    val state: StateFlow<SyncUiState> = combine(
        repository.counts,
        repository.unsynced,
        repository.lastSyncAt,
        repository.lastError,
        repository.activity,
    ) { counts, records, last, error, activity ->
        SyncUiState(counts, records, last, error, activity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun syncNow() = repository.syncNow()

    fun retry(record: SyncRecord) {
        viewModelScope.launch { repository.retry(record) }
    }
}
