package ke.afyaclaim.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ke.afyaclaim.pos.data.local.entity.SaleEntity
import ke.afyaclaim.pos.data.local.entity.SaleItemEntity
import ke.afyaclaim.pos.data.local.entity.SaleRow
import ke.afyaclaim.pos.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sales WHERE client_uuid = :clientUuid")
    suspend fun get(clientUuid: String): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE sale_client_uuid IN (:saleUuids)")
    suspend fun itemsFor(saleUuids: List<String>): List<SaleItemEntity>

    @Query("SELECT * FROM sales WHERE sync_state = 'pending' ORDER BY sold_at LIMIT :limit")
    suspend fun pending(limit: Int): List<SaleEntity>

    @Query("SELECT client_uuid FROM sales WHERE sync_state = 'pending' AND client_uuid IN (:uuids)")
    suspend fun pendingAmong(uuids: List<String>): List<String>

    @Query("UPDATE sales SET sync_state = :state, server_id = COALESCE(:serverId, server_id), sync_message = :message WHERE client_uuid = :clientUuid")
    suspend fun markState(clientUuid: String, state: SyncState, serverId: Long?, message: String?)

    @Query("SELECT COALESCE(SUM(total_cents), 0) FROM sales WHERE sold_at >= :since")
    fun observeTotalSince(since: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM sales WHERE sold_at >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT s.client_uuid, s.sold_at, s.total_cents, s.sync_state, s.sync_message,
               (SELECT GROUP_CONCAT(p.name, ', ') FROM sale_items si
                  LEFT JOIN products p ON p.id = si.product_id
                 WHERE si.sale_client_uuid = s.client_uuid) AS items_label,
               (SELECT COALESCE(SUM(si.quantity), 0) FROM sale_items si
                 WHERE si.sale_client_uuid = s.client_uuid) AS item_count,
               (SELECT COUNT(*) FROM claims c WHERE c.sale_client_uuid = s.client_uuid) AS claim_count
        FROM sales s
        ORDER BY s.sold_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<SaleRow>>

    @Query(
        """
        SELECT s.client_uuid, s.sold_at, s.total_cents, s.sync_state, s.sync_message,
               (SELECT GROUP_CONCAT(p.name, ', ') FROM sale_items si
                  LEFT JOIN products p ON p.id = si.product_id
                 WHERE si.sale_client_uuid = s.client_uuid) AS items_label,
               (SELECT COALESCE(SUM(si.quantity), 0) FROM sale_items si
                 WHERE si.sale_client_uuid = s.client_uuid) AS item_count,
               (SELECT COUNT(*) FROM claims c WHERE c.sale_client_uuid = s.client_uuid) AS claim_count
        FROM sales s
        WHERE s.sync_state != 'synced'
        ORDER BY s.sold_at DESC
        """,
    )
    fun observeUnsynced(): Flow<List<SaleRow>>

    @Query("UPDATE sales SET sync_state = 'pending', sync_message = NULL WHERE client_uuid = :clientUuid")
    suspend fun retry(clientUuid: String)
}
