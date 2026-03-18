package ke.afyaclaim.pos.data.repository

import ke.afyaclaim.pos.data.local.entity.ClaimRow
import ke.afyaclaim.pos.data.local.entity.ProductEntity
import ke.afyaclaim.pos.data.local.entity.SaleRow
import ke.afyaclaim.pos.data.local.entity.StockRow
import ke.afyaclaim.pos.domain.model.ClaimCard
import ke.afyaclaim.pos.domain.model.ClaimStatus
import ke.afyaclaim.pos.domain.model.Product
import ke.afyaclaim.pos.domain.model.ProductCategory
import ke.afyaclaim.pos.domain.model.SaleSummary
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.TestResult

fun ProductEntity.toDomain() = Product(
    id = id,
    sku = sku,
    name = name,
    category = ProductCategory.fromWire(category),
    unitPriceCents = unitPriceCents,
    subsidyCents = subsidyCents,
    subsidised = subsidised,
)

fun StockRow.toDomain() = StockItem(
    product = product.toDomain(),
    quantityOnHand = quantityOnHand ?: 0,
    reorderLevel = reorderLevel ?: 0,
    pendingAdjustments = pendingAdjustments,
)

fun SaleRow.toDomain() = SaleSummary(
    clientUuid = clientUuid,
    soldAt = soldAt,
    totalCents = totalCents,
    itemsLabel = itemsLabel ?: "Sale",
    itemCount = itemCount,
    claimCount = claimCount,
    syncState = syncState,
)

fun ClaimRow.toDomain() = ClaimCard(
    clientUuid = claim.clientUuid,
    reference = claim.reference,
    productName = productName ?: "Product #${claim.productId}",
    quantity = claim.quantity,
    amountCents = claim.amountCents,
    testResult = TestResult.fromWire(claim.testResult),
    status = ClaimStatus.fromWire(claim.status),
    rejectionReason = claim.rejectionReason,
    submittedAt = claim.submittedAt,
    syncState = claim.syncState,
)
