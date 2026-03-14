package ke.afyaclaim.pos.data.sync

import androidx.room.withTransaction
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.local.entity.ClaimEntity
import ke.afyaclaim.pos.data.local.entity.InventoryEntity
import ke.afyaclaim.pos.data.local.entity.ProductEntity
import ke.afyaclaim.pos.data.prefs.SyncCursorStore
import ke.afyaclaim.pos.data.remote.AfyaApi
import ke.afyaclaim.pos.data.remote.dto.ClaimPushDto
import ke.afyaclaim.pos.data.remote.dto.ClaimStatusDto
import ke.afyaclaim.pos.data.remote.dto.PatientDto
import ke.afyaclaim.pos.data.remote.dto.SaleItemDto
import ke.afyaclaim.pos.data.remote.dto.SalePushDto
import ke.afyaclaim.pos.data.remote.dto.StockAdjustmentPushDto
import ke.afyaclaim.pos.data.remote.dto.SyncPullResponse
import ke.afyaclaim.pos.data.remote.dto.SyncPushRequest
import ke.afyaclaim.pos.domain.model.RecordType
import ke.afyaclaim.pos.domain.model.SyncState
import ke.afyaclaim.pos.domain.sync.PushResult
import ke.afyaclaim.pos.domain.sync.Reconciliation
import ke.afyaclaim.pos.domain.sync.SyncReconciler
import java.time.Instant
import javax.inject.Inject

data class SyncOutcome(
    val pushed: Int,
    val synced: Int,
    val conflicts: Int,
    val rejected: Int,
    val claimsUpdated: Int,
)

/**
 * Push-then-pull replication against /api/v1/sync.
 *
 * Push sends pending rows in batches of [batchSize] per record type and reconciles each result.
 * Pull applies server deltas: products and inventory are overwritten (server wins), local
 * still-pending deltas are re-applied on top, and claim statuses flow down onto local cards.
 */
class SyncEngine @Inject constructor(
    private val api: AfyaApi,
    private val db: AfyaDatabase,
    private val cursorStore: SyncCursorStore,
) {
    private val sales = db.saleDao()
    private val claims = db.claimDao()
    private val adjustments = db.stockAdjustmentDao()
    private val catalog = db.catalogDao()

    var batchSize: Int = DEFAULT_BATCH_SIZE

    suspend fun run(now: () -> Long = System::currentTimeMillis): SyncOutcome {
        val push = push()
        val claimsUpdated = pull(now)
        return push.copy(claimsUpdated = claimsUpdated)
    }

    suspend fun push(): SyncOutcome {
        var pushed = 0
        var synced = 0
        var conflicts = 0
        var rejected = 0
        repeat(MAX_ROUNDS) {
            val pendingSales = sales.pending(batchSize)
            val saleUuids = pendingSales.map { it.clientUuid }.toSet()
            val candidateClaims = claims.pending(batchSize)
            val salesStillPending = candidateClaims.mapNotNull { it.saleClientUuid }
                .distinct()
                .let { if (it.isEmpty()) emptyList() else sales.pendingAmong(it) }
                .toSet()
            val readyClaimIds = SyncReconciler.claimsReadyToSend(
                candidateClaims.map { it.clientUuid to it.saleClientUuid },
                saleUuids,
                salesStillPending,
            ).toSet()
            val pendingClaims = candidateClaims.filter { it.clientUuid in readyClaimIds }
            val pendingAdjustments = adjustments.pending(batchSize)

            if (pendingSales.isEmpty() && pendingClaims.isEmpty() && pendingAdjustments.isEmpty()) {
                return SyncOutcome(pushed, synced, conflicts, rejected, 0)
            }

            val items = if (saleUuids.isEmpty()) emptyList() else sales.itemsFor(saleUuids.toList())
            val itemsBySale = items.groupBy { it.saleClientUuid }
            val request = SyncPushRequest(
                sales = pendingSales.map { sale ->
                    SalePushDto(
                        clientUuid = sale.clientUuid,
                        soldAt = Instant.ofEpochMilli(sale.soldAt).toString(),
                        patient = if (sale.ageBand != null && sale.sex != null) {
                            PatientDto(sale.pseudonym, sale.ageBand, sale.sex)
                        } else {
                            null
                        },
                        totalCents = sale.totalCents,
                        items = itemsBySale[sale.clientUuid].orEmpty().map {
                            SaleItemDto(it.productId, it.quantity, it.unitPriceCents)
                        },
                    )
                },
                claims = pendingClaims.map { claim ->
                    ClaimPushDto(
                        clientUuid = claim.clientUuid,
                        saleClientUuid = claim.saleClientUuid,
                        productId = claim.productId,
                        quantity = claim.quantity,
                        amountCents = claim.amountCents,
                        testResult = claim.testResult,
                        submittedAt = Instant.ofEpochMilli(claim.submittedAt).toString(),
                    )
                },
                stockAdjustments = pendingAdjustments.map {
                    StockAdjustmentPushDto(it.clientUuid, it.productId, it.delta, it.reason)
                },
            )
            val sent = buildSet {
                pendingSales.forEach { add(RecordType.SALE to it.clientUuid) }
                pendingClaims.forEach { add(RecordType.CLAIM to it.clientUuid) }
                pendingAdjustments.forEach { add(RecordType.STOCK_ADJUSTMENT to it.clientUuid) }
            }

            val response = api.push(request)
            val reconciliations = SyncReconciler.reconcile(
                sent,
                response.results.map { PushResult(it.type, it.clientUuid, it.status, it.serverId, it.message) },
            )
            apply(reconciliations)

            pushed += sent.size
            synced += reconciliations.count { it.state == SyncState.SYNCED }
            conflicts += reconciliations.count { it.state == SyncState.CONFLICT }
            rejected += reconciliations.count { it.state == SyncState.REJECTED }

            val fullBatch = pendingSales.size == batchSize ||
                candidateClaims.size == batchSize ||
                pendingAdjustments.size == batchSize
            // Stop when nothing moved (server ignored everything) or there is nothing more to send.
            if (reconciliations.isEmpty() || !fullBatch) {
                return SyncOutcome(pushed, synced, conflicts, rejected, 0)
            }
        }
        return SyncOutcome(pushed, synced, conflicts, rejected, 0)
    }

    suspend fun apply(reconciliations: List<Reconciliation>) {
        if (reconciliations.isEmpty()) return
        db.withTransaction {
            reconciliations.forEach { r ->
                when (r.type) {
                    RecordType.SALE -> sales.markState(r.clientUuid, r.state, r.serverId, r.message)
                    RecordType.CLAIM -> claims.markState(r.clientUuid, r.state, r.serverId, r.message)
                    RecordType.STOCK_ADJUSTMENT ->
                        adjustments.markState(r.clientUuid, r.state, r.serverId, r.message)
                }
            }
        }
    }

    /** Pulls server deltas since the stored cursor. Returns the number of claim cards updated. */
    suspend fun pull(now: () -> Long = System::currentTimeMillis): Int {
        val response = api.pull(cursorStore.cursor())
        val updated = applyPull(response)
        cursorStore.saveCursor(response.cursor ?: Instant.ofEpochMilli(now()).toString(), now())
        return updated
    }

    suspend fun applyPull(response: SyncPullResponse): Int = db.withTransaction {
        if (response.products.isNotEmpty()) {
            catalog.upsertProducts(
                response.products.map {
                    ProductEntity(it.id, it.sku, it.name, it.category, it.unitPriceCents, it.subsidyCents, it.subsidised)
                },
            )
        }
        if (response.inventory.isNotEmpty()) {
            // Server wins: overwrite quantities, then replay deltas the server has not seen yet.
            catalog.upsertInventory(
                response.inventory.map {
                    InventoryEntity(it.productId, it.id, it.quantityOnHand, it.reorderLevel, it.updatedAt)
                },
            )
            val pulledProducts = response.inventory.map { it.productId }.toSet()
            pendingLocalDeltas().filterKeys { it in pulledProducts }.forEach { (productId, delta) ->
                if (delta != 0) catalog.applyDelta(productId, delta)
            }
        }
        response.claims.count { applyClaim(it) }
    }

    private suspend fun applyClaim(dto: ClaimStatusDto): Boolean {
        val clientUuid = dto.clientUuid ?: "server-${dto.id}"
        val touched = claims.applyServerStatus(
            clientUuid = clientUuid,
            status = dto.status,
            reference = dto.reference,
            rejectionReason = dto.rejectionReason,
            serverId = dto.id,
            updatedAt = dto.updatedAt,
        )
        if (touched == 0) {
            // Claim created on another device or before a reinstall: keep a read-only copy.
            claims.insertAll(
                listOf(
                    ClaimEntity(
                        clientUuid = clientUuid,
                        saleClientUuid = null,
                        productId = dto.productId,
                        quantity = dto.quantity,
                        amountCents = dto.amountCents,
                        testResult = dto.testResult,
                        submittedAt = dto.submittedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                            ?: dto.updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                            ?: 0L,
                        status = dto.status,
                        reference = dto.reference,
                        rejectionReason = dto.rejectionReason,
                        syncState = SyncState.SYNCED,
                        serverId = dto.id,
                        updatedAt = dto.updatedAt,
                    ),
                ),
            )
        }
        return true
    }

    /** Net stock movement per product from rows the server has not acknowledged yet. */
    private suspend fun pendingLocalDeltas(): Map<Long, Int> {
        val deltas = mutableMapOf<Long, Int>()
        adjustments.pending(Int.MAX_VALUE).forEach {
            deltas[it.productId] = (deltas[it.productId] ?: 0) + it.delta
        }
        val pendingSaleIds = sales.pending(Int.MAX_VALUE).map { it.clientUuid }
        if (pendingSaleIds.isNotEmpty()) {
            sales.itemsFor(pendingSaleIds).forEach {
                deltas[it.productId] = (deltas[it.productId] ?: 0) - it.quantity
            }
        }
        return deltas
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        private const val MAX_ROUNDS = 20
    }
}
