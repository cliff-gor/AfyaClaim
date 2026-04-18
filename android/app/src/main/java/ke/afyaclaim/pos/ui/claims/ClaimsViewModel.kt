package ke.afyaclaim.pos.ui.claims

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.data.repository.ClaimRepository
import ke.afyaclaim.pos.domain.model.ClaimCard
import ke.afyaclaim.pos.domain.model.ClaimStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ClaimsUiState(
    val claims: List<ClaimCard> = emptyList(),
    val filter: ClaimStatus? = null,
    val countByStatus: Map<ClaimStatus, Int> = emptyMap(),
    val total: Int = 0,
    val awaitingCents: Long = 0,
    val approvedCents: Long = 0,
    val paidCents: Long = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class ClaimsViewModel @Inject constructor(repository: ClaimRepository) : ViewModel() {
    private val filter = MutableStateFlow<ClaimStatus?>(null)

    val state: StateFlow<ClaimsUiState> = combine(repository.claims(), filter) { all, f ->
        ClaimsUiState(
            claims = if (f == null) all else all.filter { it.status == f },
            filter = f,
            countByStatus = all.groupingBy { it.status }.eachCount(),
            total = all.size,
            awaitingCents = all.filter { it.status == ClaimStatus.SUBMITTED || it.status == ClaimStatus.UNDER_REVIEW }.sumOf { it.amountCents },
            approvedCents = all.filter { it.status == ClaimStatus.APPROVED }.sumOf { it.amountCents },
            paidCents = all.filter { it.status == ClaimStatus.PAID }.sumOf { it.amountCents },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClaimsUiState())

    fun setFilter(status: ClaimStatus?) {
        filter.value = status
    }
}
