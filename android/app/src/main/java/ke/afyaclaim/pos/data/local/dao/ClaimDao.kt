package ke.afyaclaim.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ke.afyaclaim.pos.data.local.entity.ClaimEntity
import ke.afyaclaim.pos.data.local.entity.ClaimRow
import ke.afyaclaim.pos.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface ClaimDao {
    @Insert
    suspend fun insertAll(claims: List<ClaimEntity>)

    @Query("SELECT * FROM claims WHERE client_uuid = :clientUuid")
    suspend fun get(clientUuid: String): ClaimEntity?

    @Query("SELECT * FROM claims WHERE sync_state = 'pending' ORDER BY submitted_at LIMIT :limit")
    suspend fun pending(limit: Int): List<ClaimEntity>

    @Query("UPDATE claims SET sync_state = :state, server_id = COALESCE(:serverId, server_id), sync_message = :message WHERE client_uuid = :clientUuid")
    suspend fun markState(clientUuid: String, state: SyncState, serverId: Long?, message: String?)

    /** Applies a server-side status change; returns rows touched (0 when the claim is unknown locally). */
    @Query(
        """
        UPDATE claims SET status = :status, reference = :reference, rejection_reason = :rejectionReason,
               server_id = :serverId, updated_at = :updatedAt,
               sync_state = CASE WHEN sync_state = 'pending' THEN 'synced' ELSE sync_state END
        WHERE client_uuid = :clientUuid
        """,
    )
    suspend fun applyServerStatus(
        clientUuid: String,
        status: String,
        reference: String?,
        rejectionReason: String?,
        serverId: Long,
        updatedAt: String?,
    ): Int

    @Query(
        """
        SELECT c.*, p.name AS product_name FROM claims c
        LEFT JOIN products p ON p.id = c.product_id
        ORDER BY c.submitted_at DESC
        """,
    )
    fun observeAll(): Flow<List<ClaimRow>>

    @Query(
        """
        SELECT c.*, p.name AS product_name FROM claims c
        LEFT JOIN products p ON p.id = c.product_id
        WHERE c.sync_state != 'synced'
        ORDER BY c.submitted_at DESC
        """,
    )
    fun observeUnsynced(): Flow<List<ClaimRow>>

    @Query("SELECT COUNT(*) FROM claims WHERE submitted_at >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("UPDATE claims SET sync_state = 'pending', sync_message = NULL WHERE client_uuid = :clientUuid")
    suspend fun retry(clientUuid: String)
}
