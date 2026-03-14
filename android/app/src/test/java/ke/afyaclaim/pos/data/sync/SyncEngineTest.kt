package ke.afyaclaim.pos.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ke.afyaclaim.pos.data.local.AfyaDatabase
import ke.afyaclaim.pos.data.local.entity.ClaimEntity
import ke.afyaclaim.pos.data.local.entity.InventoryEntity
import ke.afyaclaim.pos.data.local.entity.ProductEntity
import ke.afyaclaim.pos.data.local.entity.SaleEntity
import ke.afyaclaim.pos.data.local.entity.SaleItemEntity
import ke.afyaclaim.pos.data.local.entity.StockAdjustmentEntity
import ke.afyaclaim.pos.data.prefs.SyncCursorStore
import ke.afyaclaim.pos.data.remote.AfyaApi
import ke.afyaclaim.pos.data.remote.dto.ClaimStatusDto
import ke.afyaclaim.pos.data.remote.dto.InventoryDto
import ke.afyaclaim.pos.data.remote.dto.LoginRequest
import ke.afyaclaim.pos.data.remote.dto.LoginResponse
import ke.afyaclaim.pos.data.remote.dto.ProductDto
import ke.afyaclaim.pos.data.remote.dto.ProductsResponse
import ke.afyaclaim.pos.data.remote.dto.SyncPullResponse
import ke.afyaclaim.pos.data.remote.dto.SyncPushRequest
import ke.afyaclaim.pos.data.remote.dto.SyncPushResponse
import ke.afyaclaim.pos.data.remote.dto.SyncResultDto
import ke.afyaclaim.pos.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exercises push/pull reconciliation end-to-end against an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncEngineTest {
    private lateinit var db: AfyaDatabase
    private lateinit var api: FakeApi
    private lateinit var cursor: FakeCursor
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AfyaDatabase::class.java).allowMainThreadQueries().build()
        api = FakeApi()
        cursor = FakeCursor()
        engine = SyncEngine(api, db, cursor)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedCatalog(qty: Int = 10) {
        db.catalogDao().upsertProducts(listOf(ProductEntity(1, "RDT", "Malaria RDT", "malaria_test", 15_000, 10_000, true)))
        db.catalogDao().upsertInventory(listOf(InventoryEntity(1, 100, qty, 5, null)))
    }

    private suspend fun seedSale(uuid: String, qty: Int, soldAt: Long = 1_000) {
        db.saleDao().insertSale(SaleEntity(uuid, soldAt, 15_000L * qty, null, "25-49", "female"))
        db.saleDao().insertItems(listOf(SaleItemEntity(saleClientUuid = uuid, productId = 1, quantity = qty, unitPriceCents = 15_000)))
    }

    @Test
    fun `accepted and duplicate results mark records synced`() = runTest {
        seedCatalog()
        seedSale("s1", 1)
        seedSale("s2", 1)
        db.claimDao().insertAll(listOf(ClaimEntity("c1", "s1", 1, 1, 10_000, "positive", 1_000)))
        api.respond = { req ->
            req.sales.map { SyncResultDto("sale", it.clientUuid, if (it.clientUuid == "s2") "duplicate" else "accepted", 50) } +
                req.claims.map { SyncResultDto("claim", it.clientUuid, "accepted", 70) }
        }

        val outcome = engine.push()

        assertEquals(3, outcome.synced)
        assertEquals(SyncState.SYNCED, db.saleDao().get("s1")!!.syncState)
        assertEquals(SyncState.SYNCED, db.saleDao().get("s2")!!.syncState)
        assertEquals(70L, db.claimDao().get("c1")!!.serverId)
        // Patient fields and claim linkage travel as the contract specifies.
        val sentSale = api.pushes.single().sales.first { it.clientUuid == "s1" }
        assertEquals("25-49", sentSale.patient!!.ageBand)
        assertEquals("s1", api.pushes.single().claims.single().saleClientUuid)
    }

    @Test
    fun `conflict and rejected are kept with their messages`() = runTest {
        seedCatalog()
        db.stockAdjustmentDao().insert(StockAdjustmentEntity("a1", 1, -50, "damaged", 1_000))
        seedSale("s1", 1)
        api.respond = {
            listOf(
                SyncResultDto("stock_adjustment", "a1", "conflict", null, "Would make stock negative"),
                SyncResultDto("sale", "s1", "rejected", null, "Pharmacy suspended"),
            )
        }

        engine.push()

        val adj = db.stockAdjustmentDao().get("a1")!!
        assertEquals(SyncState.CONFLICT, adj.syncState)
        assertEquals("Would make stock negative", adj.syncMessage)
        assertEquals(SyncState.REJECTED, db.saleDao().get("s1")!!.syncState)
        assertEquals("Pharmacy suspended", db.saleDao().get("s1")!!.syncMessage)
    }

    @Test
    fun `records without a result stay pending for the next run`() = runTest {
        seedCatalog()
        seedSale("s1", 1)
        api.respond = { emptyList() }
        engine.push()
        assertEquals(SyncState.PENDING, db.saleDao().get("s1")!!.syncState)
    }

    @Test
    fun `pending rows are pushed in batches of the configured size`() = runTest {
        seedCatalog(qty = 1_000)
        repeat(7) { seedSale("s$it", 1, soldAt = it.toLong()) }
        engine.batchSize = 3
        api.respond = { req -> req.sales.map { SyncResultDto("sale", it.clientUuid, "accepted", 1) } }

        engine.push()

        assertEquals(listOf(3, 3, 1), api.pushes.map { it.sales.size })
        assertEquals(0, db.saleDao().pending(100).size)
    }

    @Test
    fun `claims are held back until their sale is sent`() = runTest {
        seedCatalog(qty = 1_000)
        seedSale("s0", 1, soldAt = 0)
        seedSale("s1", 1, soldAt = 1)
        db.claimDao().insertAll(listOf(ClaimEntity("c1", "s1", 1, 1, 10_000, null, 1)))
        engine.batchSize = 1
        api.respond = { req ->
            req.sales.map { SyncResultDto("sale", it.clientUuid, "accepted", 1) } +
                req.claims.map { SyncResultDto("claim", it.clientUuid, "accepted", 2) }
        }

        engine.push()

        assertEquals(emptyList<String>(), api.pushes[0].claims.map { it.clientUuid })
        assertEquals(listOf("s1"), api.pushes[1].sales.map { it.clientUuid })
        assertEquals(listOf("c1"), api.pushes[1].claims.map { it.clientUuid })
    }

    @Test
    fun `pull overwrites inventory (server wins) and replays still-pending deltas`() = runTest {
        seedCatalog(qty = 10)
        // A sale not yet acknowledged by the server: local stock already went 10 -> 8.
        seedSale("s-pending", 2)
        db.catalogDao().applyDelta(1, -2)
        // A conflicted adjustment must NOT be replayed: server wins.
        db.stockAdjustmentDao().insert(StockAdjustmentEntity("a-conf", 1, -5, "damaged", 1, SyncState.CONFLICT))
        api.pullResponse = SyncPullResponse(
            inventory = listOf(InventoryDto(100, 1, 20, 6, "2026-10-16T08:00:00Z")),
            cursor = "2026-10-16T08:00:00Z",
        )

        engine.pull()

        val inv = db.catalogDao().inventoryFor(1)!!
        assertEquals(18, inv.quantityOnHand)
        assertEquals(6, inv.reorderLevel)
        assertEquals("2026-10-16T08:00:00Z", cursor.saved)
    }

    @Test
    fun `pull sends stored cursor and updates claim status from reviewers`() = runTest {
        seedCatalog()
        db.claimDao().insertAll(listOf(ClaimEntity("c1", null, 1, 1, 10_000, null, 1, syncState = SyncState.SYNCED)))
        cursor.saved = "2026-10-15T00:00:00Z"
        api.pullResponse = SyncPullResponse(
            products = listOf(ProductDto(2, "ACT", "AL 24", "antimalarial", 30_000, 20_000, true)),
            claims = listOf(
                ClaimStatusDto(9, "c1", "AC-2026-000009", 1, 1, 10_000, "rejected", "Duplicate patient"),
                ClaimStatusDto(10, "c-other-device", "AC-2026-000010", 1, 2, 20_000, "paid", updatedAt = "2026-10-16T09:00:00Z"),
            ),
            cursor = "2026-10-16T09:00:00Z",
        )

        val updated = engine.pull()

        assertEquals("2026-10-15T00:00:00Z", api.lastSince)
        assertEquals(2, updated)
        val c1 = db.claimDao().get("c1")!!
        assertEquals("rejected", c1.status)
        assertEquals("Duplicate patient", c1.rejectionReason)
        assertEquals("AC-2026-000009", c1.reference)
        val other = db.claimDao().get("c-other-device")!!
        assertEquals("paid", other.status)
        assertEquals(SyncState.SYNCED, other.syncState)
        assertEquals("2026-10-16T09:00:00Z", cursor.saved)
    }

    @Test
    fun `first pull sends no cursor`() = runTest {
        api.pullResponse = SyncPullResponse(cursor = "c")
        engine.pull()
        assertNull(api.lastSince)
    }

    private class FakeApi : AfyaApi {
        val pushes = mutableListOf<SyncPushRequest>()
        var respond: (SyncPushRequest) -> List<SyncResultDto> = { emptyList() }
        var pullResponse = SyncPullResponse()
        var lastSince: String? = "unset"

        override suspend fun login(body: LoginRequest): LoginResponse = error("not used")
        override suspend fun products(): ProductsResponse = ProductsResponse(emptyList())
        override suspend fun push(body: SyncPushRequest): SyncPushResponse {
            pushes += body
            return SyncPushResponse(respond(body))
        }
        override suspend fun pull(since: String?): SyncPullResponse {
            lastSince = since
            return pullResponse
        }
    }

    private class FakeCursor : SyncCursorStore {
        var saved: String? = null
        override suspend fun cursor(): String? = saved
        override suspend fun saveCursor(cursor: String, syncedAt: Long) {
            saved = cursor
        }
    }
}
