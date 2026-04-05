package ke.afyaclaim.pos.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ke.afyaclaim.pos.core.Money
import ke.afyaclaim.pos.data.repository.InventoryRepository
import ke.afyaclaim.pos.data.repository.SaleRepository
import ke.afyaclaim.pos.domain.model.AgeBand
import ke.afyaclaim.pos.domain.model.ProductCategory
import ke.afyaclaim.pos.domain.model.Sex
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.TestResult
import ke.afyaclaim.pos.domain.sale.CartLine
import ke.afyaclaim.pos.domain.sale.DraftProblem
import ke.afyaclaim.pos.domain.sale.SaleDraft
import ke.afyaclaim.pos.domain.sale.SaleRules
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaleForm(
    val quantities: Map<Long, Int> = emptyMap(),
    val query: String = "",
    val category: ProductCategory? = null,
    val ageBand: AgeBand? = null,
    val sex: Sex? = null,
    val phone: String = "",
    val testResult: TestResult? = null,
    val attachClaim: Boolean = true,
    val saving: Boolean = false,
    val problem: String? = null,
)

data class NewSaleUiState(
    val form: SaleForm = SaleForm(),
    val catalogue: List<StockItem> = emptyList(),
    val visible: List<StockItem> = emptyList(),
    val draft: SaleDraft = SaleDraft(emptyList(), null, null, null, null, true),
    val categories: List<ProductCategory> = emptyList(),
    val loaded: Boolean = false,
) {
    val itemCount: Int get() = draft.lines.sumOf { it.quantity }
    val claimCount: Int get() = if (form.attachClaim) draft.subsidisedLines.size else 0
}

@HiltViewModel
class NewSaleViewModel @Inject constructor(
    private val sales: SaleRepository,
    inventory: InventoryRepository,
) : ViewModel() {
    private val form = MutableStateFlow(SaleForm())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    val state: StateFlow<NewSaleUiState> = combine(form, inventory.stock()) { f, stock ->
        val lines = stock.mapNotNull { item ->
            f.quantities[item.product.id]?.takeIf { it > 0 }?.let { CartLine(item.product, it) }
        }
        val draft = SaleDraft(
            lines = lines,
            ageBand = f.ageBand,
            sex = f.sex,
            phone = f.phone.ifBlank { null },
            testResult = f.testResult,
            attachClaim = f.attachClaim,
        )
        val q = f.query.trim().lowercase()
        NewSaleUiState(
            form = f,
            catalogue = stock,
            visible = stock.filter { item ->
                (f.category == null || item.product.category == f.category) &&
                    (q.isEmpty() || item.product.name.lowercase().contains(q) || item.product.sku.lowercase().contains(q))
            },
            draft = draft,
            categories = stock.map { it.product.category }.distinct(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewSaleUiState())

    fun setQuantity(productId: Long, qty: Int) = form.update {
        it.copy(quantities = it.quantities + (productId to qty), problem = null)
    }
    fun setQuery(q: String) = form.update { it.copy(query = q) }
    fun setCategory(cat: ProductCategory?) = form.update { it.copy(category = cat) }
    fun setAgeBand(band: AgeBand) = form.update { it.copy(ageBand = if (it.ageBand == band) null else band, problem = null) }
    fun setSex(sex: Sex) = form.update { it.copy(sex = if (it.sex == sex) null else sex, problem = null) }
    fun setPhone(phone: String) = form.update { it.copy(phone = phone.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16), problem = null) }
    fun setTestResult(result: TestResult) = form.update { it.copy(testResult = result, problem = null) }
    fun setAttachClaim(on: Boolean) = form.update { it.copy(attachClaim = on, problem = null) }
    fun clear() = form.update { SaleForm(attachClaim = it.attachClaim) }

    fun save() {
        val current = state.value
        if (form.value.saving) return
        val stockMap = current.catalogue.associate { it.product.id to it.quantityOnHand }
        val problem = SaleRules.validate(current.draft, stockMap)
        if (problem != null) {
            form.update { it.copy(problem = problem.describe()) }
            return
        }
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { sales.save(current.draft) }
                .onSuccess { saved ->
                    form.value = SaleForm(attachClaim = form.value.attachClaim)
                    val claims = if (saved.claimCount > 0) " · ${saved.claimCount} claim${if (saved.claimCount == 1) "" else "s"} queued" else ""
                    events.send("Sale saved · ${Money.format(saved.totalCents)}$claims")
                }
                .onFailure { e ->
                    form.update { it.copy(saving = false, problem = e.message ?: "Could not save sale") }
                }
        }
    }

    private fun DraftProblem.describe(): String = when (this) {
        DraftProblem.EmptyCart -> "Add at least one product."
        DraftProblem.MissingTestResult -> "Record the malaria test result."
        DraftProblem.MissingPatientForClaim -> "Choose the patient's age band and sex to attach a claim."
        DraftProblem.InvalidPhone -> "Phone number doesn't look like a Kenyan mobile number."
        is DraftProblem.InsufficientStock -> "Only $available of $productName in stock."
    }
}
