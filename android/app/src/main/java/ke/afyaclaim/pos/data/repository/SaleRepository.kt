package ke.afyaclaim.pos.data.repository

import androidx.room.withTransaction
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.local.entity.ClaimEntity
import ke.afyaclaim.pos.data.local.entity.SaleEntity
import ke.afyaclaim.pos.data.local.entity.SaleItemEntity
import ke.afyaclaim.pos.data.sync.SyncScheduler
import ke.afyaclaim.pos.domain.model.Product
import ke.afyaclaim.pos.domain.model.SaleSummary
import ke.afyaclaim.pos.domain.sale.PseudonymHasher
import ke.afyaclaim.pos.domain.sale.SaleDraft
import ke.afyaclaim.pos.domain.sale.SaleRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SavedSale(val clientUuid: String, val claimCount: Int, val totalCents: Long)

@Singleton
class SaleRepository @Inject constructor(
    private val db: AfyaDatabase,
    private val hasher: PseudonymHasher,
    private val scheduler: SyncScheduler,
) {
    private val saleDao = db.saleDao()
    private val claimDao = db.claimDao()
    private val catalog = db.catalogDao()

    fun products(): Flow<List<Product>> = catalog.observeProducts().map { list -> list.map { it.toDomain() } }

    fun recentSales(limit: Int = 8): Flow<List<SaleSummary>> =
        saleDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    fun totalSince(since: Long): Flow<Long> = saleDao.observeTotalSince(since)
    fun countSince(since: Long): Flow<Int> = saleDao.observeCountSince(since)
    fun claimsSince(since: Long): Flow<Int> = claimDao.observeCountSince(since)

    suspend fun stockOnHand(): Map<Long, Int> =
        catalog.allInventory().associate { it.productId to it.quantityOnHand }

    /**
     * Persists the sale, its items and claims in one transaction and decrements local stock
     * immediately. Nothing touches the network here; a sync is queued afterwards.
     */
    suspend fun save(draft: SaleDraft, now: Long = System.currentTimeMillis()): SavedSale {
        val lines = draft.lines.filter { it.quantity > 0 }
        val saleUuid = UUID.randomUUID().toString()
        val hasPatient = draft.ageBand != null && draft.sex != null
        val claimLines = SaleRules.claimsFor(draft)
        db.withTransaction {
            saleDao.insertSale(
                SaleEntity(
                    clientUuid = saleUuid,
                    soldAt = now,
                    totalCents = lines.sumOf { it.lineTotalCents },
                    pseudonym = if (hasPatient) hasher.pseudonymFor(draft.phone) else null,
                    ageBand = draft.ageBand?.wire?.takeIf { hasPatient },
                    sex = draft.sex?.wire?.takeIf { hasPatient },
                ),
            )
            saleDao.insertItems(
                lines.map { SaleItemEntity(saleClientUuid = saleUuid, productId = it.product.id, quantity = it.quantity, unitPriceCents = it.product.unitPriceCents) },
            )
            if (claimLines.isNotEmpty()) {
                claimDao.insertAll(
                    claimLines.map {
                        ClaimEntity(
                            clientUuid = UUID.randomUUID().toString(),
                            saleClientUuid = saleUuid,
                            productId = it.productId,
                            quantity = it.quantity,
                            amountCents = it.amountCents,
                            testResult = it.testResult?.wire,
                            submittedAt = now,
                        )
                    },
                )
            }
            lines.forEach { catalog.applyDelta(it.product.id, -it.quantity) }
        }
        scheduler.syncNow()
        return SavedSale(saleUuid, claimLines.size, lines.sumOf { it.lineTotalCents })
    }
}
