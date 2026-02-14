package ke.afyaclaim.pos.domain.model

data class Product(
    val id: Long,
    val sku: String,
    val name: String,
    val category: ProductCategory,
    val unitPriceCents: Long,
    val subsidyCents: Long,
    val subsidised: Boolean,
)

data class StockItem(
    val product: Product,
    val quantityOnHand: Int,
    val reorderLevel: Int,
    val pendingAdjustments: Int,
) {
    val isOut: Boolean get() = quantityOnHand <= 0
    val isLow: Boolean get() = !isOut && quantityOnHand <= reorderLevel
}

data class Session(
    val token: String,
    val userId: Long,
    val name: String,
    val email: String,
    val pharmacyName: String,
    val pharmacyTown: String,
) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }
}

data class SaleSummary(
    val clientUuid: String,
    val soldAt: Long,
    val totalCents: Long,
    val itemsLabel: String,
    val itemCount: Int,
    val claimCount: Int,
    val syncState: SyncState,
)

data class ClaimCard(
    val clientUuid: String,
    val reference: String?,
    val productName: String,
    val quantity: Int,
    val amountCents: Long,
    val testResult: TestResult?,
    val status: ClaimStatus,
    val rejectionReason: String?,
    val submittedAt: Long,
    val syncState: SyncState,
)

data class SyncRecord(
    val type: RecordType,
    val clientUuid: String,
    val title: String,
    val subtitle: String,
    val createdAt: Long,
    val state: SyncState,
    val message: String?,
)

data class SyncCounts(val pending: Int = 0, val conflict: Int = 0, val rejected: Int = 0) {
    val needsAttention: Int get() = conflict + rejected
}
