package ke.afyaclaim.pos.domain.sale

import ke.afyaclaim.pos.domain.model.AgeBand
import ke.afyaclaim.pos.domain.model.Product
import ke.afyaclaim.pos.domain.model.ProductCategory
import ke.afyaclaim.pos.domain.model.Sex
import ke.afyaclaim.pos.domain.model.TestResult

data class CartLine(val product: Product, val quantity: Int) {
    val lineTotalCents: Long get() = product.unitPriceCents * quantity
    val claimableCents: Long get() = if (product.subsidised) product.subsidyCents * quantity else 0
}

/** Everything the pharmacist entered for one sale, before it is persisted. */
data class SaleDraft(
    val lines: List<CartLine>,
    val ageBand: AgeBand?,
    val sex: Sex?,
    val phone: String?,
    val testResult: TestResult?,
    val attachClaim: Boolean,
) {
    val totalCents: Long get() = lines.sumOf { it.lineTotalCents }
    val hasMalariaTest: Boolean get() = lines.any { it.product.category == ProductCategory.MALARIA_TEST }
    val subsidisedLines: List<CartLine> get() = lines.filter { it.product.subsidised && it.quantity > 0 }
    val claimableCents: Long get() = subsidisedLines.sumOf { it.claimableCents }
}

sealed interface DraftProblem {
    data object EmptyCart : DraftProblem
    data object MissingTestResult : DraftProblem
    data object MissingPatientForClaim : DraftProblem
    data object InvalidPhone : DraftProblem
    data class InsufficientStock(val productName: String, val available: Int) : DraftProblem
}

object SaleRules {
    /**
     * Validates a draft. [stockOnHand] maps product id to local quantity; products without a row
     * are not checked (stock unknown until first sync).
     */
    fun validate(draft: SaleDraft, stockOnHand: Map<Long, Int>): DraftProblem? {
        val lines = draft.lines.filter { it.quantity > 0 }
        if (lines.isEmpty()) return DraftProblem.EmptyCart
        if (draft.hasMalariaTest && draft.testResult == null) return DraftProblem.MissingTestResult
        if (!draft.phone.isNullOrBlank() && PseudonymHasher.normalise(draft.phone) == null) {
            return DraftProblem.InvalidPhone
        }
        if (draft.attachClaim && draft.subsidisedLines.isNotEmpty() && (draft.ageBand == null || draft.sex == null)) {
            return DraftProblem.MissingPatientForClaim
        }
        lines.forEach { line ->
            val available = stockOnHand[line.product.id] ?: return@forEach
            if (line.quantity > available) return DraftProblem.InsufficientStock(line.product.name, available)
        }
        return null
    }

    /** Claims that a draft produces: one per subsidised line, amount = subsidy x quantity. */
    fun claimsFor(draft: SaleDraft): List<ClaimLine> {
        if (!draft.attachClaim) return emptyList()
        return draft.subsidisedLines.map { line ->
            ClaimLine(
                productId = line.product.id,
                quantity = line.quantity,
                amountCents = line.product.subsidyCents * line.quantity,
                testResult = if (line.product.category == ProductCategory.MALARIA_TEST) draft.testResult else null,
            )
        }
    }
}

data class ClaimLine(
    val productId: Long,
    val quantity: Int,
    val amountCents: Long,
    val testResult: TestResult?,
)
