package ke.afyaclaim.pos.data.repository

import ke.afyaclaim.pos.core.Money
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.prefs.SessionStore
import ke.afyaclaim.pos.data.sync.SyncActivity
import ke.afyaclaim.pos.data.sync.SyncScheduler
import ke.afyaclaim.pos.domain.model.RecordType
import ke.afyaclaim.pos.domain.model.SyncCounts
import ke.afyaclaim.pos.domain.model.SyncRecord
import ke.afyaclaim.pos.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val db: AfyaDatabase,
    private val sessionStore: SessionStore,
    private val scheduler: SyncScheduler,
) {
    val activity: Flow<SyncActivity> = scheduler.activity
    val lastSyncAt: Flow<Long?> = sessionStore.lastSyncAt
    val lastError: Flow<String?> = sessionStore.lastSyncError

    /** Every record that is not yet acknowledged by the server, newest first. */
    val unsynced: Flow<List<SyncRecord>> = combine(
        db.saleDao().observeUnsynced(),
        db.claimDao().observeUnsynced(),
        db.stockAdjustmentDao().observeUnsynced(),
    ) { sales, claims, adjustments ->
        val saleRecords = sales.map {
            SyncRecord(
                type = RecordType.SALE,
                clientUuid = it.clientUuid,
                title = it.itemsLabel ?: "Sale",
                subtitle = "${it.itemCount} items · ${Money.format(it.totalCents)}",
                createdAt = it.soldAt,
                state = it.syncState,
                message = it.syncMessage,
            )
        }
        val claimRecords = claims.map {
            SyncRecord(
                type = RecordType.CLAIM,
                clientUuid = it.claim.clientUuid,
                title = "Claim · ${it.productName ?: "Product"}",
                subtitle = "${it.claim.quantity} × · ${Money.format(it.claim.amountCents)}",
                createdAt = it.claim.submittedAt,
                state = it.claim.syncState,
                message = it.claim.syncMessage,
            )
        }
        val adjustmentRecords = adjustments.map {
            val sign = if (it.adjustment.delta > 0) "+" else ""
            SyncRecord(
                type = RecordType.STOCK_ADJUSTMENT,
                clientUuid = it.adjustment.clientUuid,
                title = "Stock · ${it.productName ?: "Product"}",
                subtitle = "$sign${it.adjustment.delta} · ${it.adjustment.reason.replace('_', ' ')}",
                createdAt = it.adjustment.createdAt,
                state = it.adjustment.syncState,
                message = it.adjustment.syncMessage,
            )
        }
        (saleRecords + claimRecords + adjustmentRecords).sortedByDescending { it.createdAt }
    }

    val counts: Flow<SyncCounts> = unsynced.map { records ->
        SyncCounts(
            pending = records.count { it.state == SyncState.PENDING },
            conflict = records.count { it.state == SyncState.CONFLICT },
            rejected = records.count { it.state == SyncState.REJECTED },
        )
    }

    fun syncNow() = scheduler.syncNow()

    /** Puts a conflict/rejected record back in the queue, e.g. after the pharmacist fixed stock. */
    suspend fun retry(record: SyncRecord) {
        when (record.type) {
            RecordType.SALE -> db.saleDao().retry(record.clientUuid)
            RecordType.CLAIM -> db.claimDao().retry(record.clientUuid)
            RecordType.STOCK_ADJUSTMENT -> db.stockAdjustmentDao().retry(record.clientUuid)
        }
        scheduler.syncNow()
    }
}
