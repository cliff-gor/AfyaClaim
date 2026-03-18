package ke.afyaclaim.pos.data.repository

import androidx.room.withTransaction
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.local.entity.StockAdjustmentEntity
import ke.afyaclaim.pos.data.sync.SyncScheduler
import ke.afyaclaim.pos.domain.model.StockItem
import ke.afyaclaim.pos.domain.model.StockReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val db: AfyaDatabase,
    private val scheduler: SyncScheduler,
) {
    private val catalog = db.catalogDao()
    private val adjustments = db.stockAdjustmentDao()

    fun stock(): Flow<List<StockItem>> = catalog.observeStock().map { rows -> rows.map { it.toDomain() } }

    /** Records a stock delta (never an absolute count) and applies it locally straight away. */
    suspend fun adjust(productId: Long, delta: Int, reason: StockReason, now: Long = System.currentTimeMillis()) {
        require(delta != 0) { "Delta must not be zero" }
        db.withTransaction {
            adjustments.insert(
                StockAdjustmentEntity(
                    clientUuid = UUID.randomUUID().toString(),
                    productId = productId,
                    delta = delta,
                    reason = reason.wire,
                    createdAt = now,
                ),
            )
            catalog.applyDelta(productId, delta)
        }
        scheduler.syncNow()
    }
}
