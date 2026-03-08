package ke.afyaclaim.pos.domain

import ke.afyaclaim.pos.domain.model.RecordType
import ke.afyaclaim.pos.domain.model.SyncState
import ke.afyaclaim.pos.domain.sync.PushResult
import ke.afyaclaim.pos.domain.sync.SyncReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReconcilerTest {
    private val sent = setOf(
        RecordType.SALE to "s1",
        RecordType.SALE to "s2",
        RecordType.CLAIM to "c1",
        RecordType.CLAIM to "c2",
        RecordType.STOCK_ADJUSTMENT to "a1",
    )

    private fun reconcile(vararg results: PushResult) =
        SyncReconciler.reconcile(sent, results.toList()).associateBy { it.type to it.clientUuid }

    @Test
    fun `accepted marks synced and keeps server id`() {
        val r = reconcile(PushResult("sale", "s1", "accepted", 42, null))[RecordType.SALE to "s1"]!!
        assertEquals(SyncState.SYNCED, r.state)
        assertEquals(42L, r.serverId)
        assertNull(r.message)
    }

    @Test
    fun `duplicate is treated as already synced`() {
        val r = reconcile(PushResult("claim", "c1", "duplicate", 7, "already exists"))[RecordType.CLAIM to "c1"]!!
        assertEquals(SyncState.SYNCED, r.state)
        assertEquals(7L, r.serverId)
        assertNull("duplicates should not surface a message", r.message)
    }

    @Test
    fun `conflict is surfaced with server message`() {
        val r = reconcile(PushResult("stock_adjustment", "a1", "conflict", null, "Would go negative"))
            .getValue(RecordType.STOCK_ADJUSTMENT to "a1")
        assertEquals(SyncState.CONFLICT, r.state)
        assertEquals("Would go negative", r.message)
    }

    @Test
    fun `conflict without message gets a helpful default`() {
        val r = reconcile(PushResult("stock_adjustment", "a1", "conflict", null, null))
            .getValue(RecordType.STOCK_ADJUSTMENT to "a1")
        assertTrue(r.message!!.contains("negative"))
    }

    @Test
    fun `rejected is surfaced`() {
        val r = reconcile(PushResult("claim", "c2", "rejected", null, "Product not subsidised"))
            .getValue(RecordType.CLAIM to "c2")
        assertEquals(SyncState.REJECTED, r.state)
        assertEquals("Product not subsidised", r.message)
    }

    @Test
    fun `unknown status, unknown type and unsent records are ignored`() {
        val out = reconcile(
            PushResult("sale", "s1", "queued", null, null),
            PushResult("refund", "s2", "accepted", 1, null),
            PushResult("sale", "zzz", "accepted", 1, null),
            PushResult("claim", "s1", "accepted", 1, null),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `last result for the same record wins`() {
        val out = reconcile(
            PushResult("sale", "s2", "conflict", null, "first"),
            PushResult("sale", "s2", "accepted", 9, null),
        )
        assertEquals(1, out.size)
        assertEquals(SyncState.SYNCED, out.getValue(RecordType.SALE to "s2").state)
    }

    @Test
    fun `mixed batch maps every record`() {
        val out = reconcile(
            PushResult("sale", "s1", "accepted", 1, null),
            PushResult("sale", "s2", "duplicate", 2, null),
            PushResult("claim", "c1", "accepted", 3, null),
            PushResult("claim", "c2", "rejected", null, "no"),
            PushResult("stock_adjustment", "a1", "conflict", null, "neg"),
        )
        assertEquals(5, out.size)
        assertEquals(3, out.values.count { it.state == SyncState.SYNCED })
    }

    @Test
    fun `claims wait for their pending sale unless it travels in the same batch`() {
        val ready = SyncReconciler.claimsReadyToSend(
            pendingClaims = listOf(
                "c-in-batch" to "s1",
                "c-waiting" to "s9",
                "c-sale-done" to "s-synced",
                "c-orphan" to null,
            ),
            salesInBatch = setOf("s1"),
            salesStillPending = setOf("s1", "s9"),
        )
        assertEquals(listOf("c-in-batch", "c-sale-done", "c-orphan"), ready)
    }
}
