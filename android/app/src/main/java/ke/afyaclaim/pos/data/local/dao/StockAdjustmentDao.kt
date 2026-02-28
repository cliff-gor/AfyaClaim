package ke.afyaclaim.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ke.afyaclaim.pos.data.local.entity.AdjustmentRow
import ke.afyaclaim.pos.data.local.entity.StockAdjustmentEntity
import ke.afyaclaim.pos.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {
    @Insert
    suspend fun insert(adjustment: StockAdjustmentEntity)

    @Query("SELECT * FROM stock_adjustments WHERE client_uuid = :clientUuid")
    suspend fun get(clientUuid: String): StockAdjustmentEntity?

    @Query("SELECT * FROM stock_adjustments WHERE sync_state = 'pending' ORDER BY created_at LIMIT :limit")
    suspend fun pending(limit: Int): List<StockAdjustmentEntity>

    @Query("UPDATE stock_adjustments SET sync_state = :state, server_id = COALESCE(:serverId, server_id), sync_message = :message WHERE client_uuid = :clientUuid")
    suspend fun markState(clientUuid: String, state: SyncState, serverId: Long?, message: String?)

    @Query(
        """
        SELECT a.*, p.name AS product_name FROM stock_adjustments a
        LEFT JOIN products p ON p.id = a.product_id
        WHERE a.sync_state != 'synced'
        ORDER BY a.created_at DESC
        """,
    )
    fun observeUnsynced(): Flow<List<AdjustmentRow>>

    @Query("UPDATE stock_adjustments SET sync_state = 'pending', sync_message = NULL WHERE client_uuid = :clientUuid")
    suspend fun retry(clientUuid: String)
}
