package ke.afyaclaim.pos.domain.sync

import ke.afyaclaim.pos.domain.model.RecordType
import ke.afyaclaim.pos.domain.model.SyncState

/** One per-record result returned by POST /sync. */
data class PushResult(
    val type: String,
    val clientUuid: String,
    val status: String,
    val serverId: Long?,
    val message: String?,
)

/** The local change a push result implies. */
data class Reconciliation(
    val type: RecordType,
    val clientUuid: String,
    val state: SyncState,
    val serverId: Long?,
    val message: String?,
)

/**
 * Pure conflict policy for push results (SPEC section 9):
 *  - accepted  -> synced
 *  - duplicate -> synced (the server already has this client_uuid; retries are safe)
 *  - conflict  -> conflict, surfaced on the Sync screen (e.g. stock delta would go negative)
 *  - rejected  -> rejected, surfaced on the Sync screen
 * Unknown statuses or types leave the record pending so it is retried next run.
 * Results for client_uuids that were not part of the batch are ignored.
 */
object SyncReconciler {

    fun reconcile(sent: Set<Pair<RecordType, String>>, results: List<PushResult>): List<Reconciliation> =
        results.mapNotNull { result ->
            val type = RecordType.fromWire(result.type) ?: return@mapNotNull null
            if ((type to result.clientUuid) !in sent) return@mapNotNull null
            val state = when (result.status) {
                "accepted", "duplicate" -> SyncState.SYNCED
                "conflict" -> SyncState.CONFLICT
                "rejected" -> SyncState.REJECTED
                else -> return@mapNotNull null
            }
            Reconciliation(
                type = type,
                clientUuid = result.clientUuid,
                state = state,
                serverId = result.serverId,
                message = when (state) {
                    SyncState.SYNCED -> null
                    else -> result.message ?: defaultMessage(type, state)
                },
            )
        }
            // If the server echoes a record twice, the last word wins.
            .associateBy { it.type to it.clientUuid }
            .values
            .toList()

    private fun defaultMessage(type: RecordType, state: SyncState): String = when (state) {
        SyncState.CONFLICT -> if (type == RecordType.STOCK_ADJUSTMENT) {
            "Server stock would go negative. Recount and adjust again."
        } else {
            "Server reported a conflict for this ${type.label.lowercase()}."
        }
        else -> "Server rejected this ${type.label.lowercase()}."
    }

    /**
     * Chooses which pending claims can go in a push batch. A claim waits while its sale is still
     * pending and not in this batch, so the server can link the two. Once the sale has been
     * resolved (synced, conflict or rejected) the claim is sent and the server decides.
     */
    fun claimsReadyToSend(
        pendingClaims: List<Pair<String, String?>>,
        salesInBatch: Set<String>,
        salesStillPending: Set<String>,
    ): List<String> = pendingClaims
        .filter { (_, saleUuid) -> saleUuid == null || saleUuid in salesInBatch || saleUuid !in salesStillPending }
        .map { it.first }
}
