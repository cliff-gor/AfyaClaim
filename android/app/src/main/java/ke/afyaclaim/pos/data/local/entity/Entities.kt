package ke.afyaclaim.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ke.afyaclaim.pos.domain.model.SyncState

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Long,
    val sku: String,
    val name: String,
    val category: String,
    @ColumnInfo(name = "unit_price_cents") val unitPriceCents: Long,
    @ColumnInfo(name = "subsidy_cents") val subsidyCents: Long,
    val subsidised: Boolean,
)

/** One row per product; the server is the source of truth and overwrites quantity on pull. */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "server_id") val serverId: Long?,
    @ColumnInfo(name = "quantity_on_hand") val quantityOnHand: Int,
    @ColumnInfo(name = "reorder_level") val reorderLevel: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
)

@Entity(tableName = "sales", indices = [Index("sync_state"), Index("sold_at")])
data class SaleEntity(
    @PrimaryKey @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "sold_at") val soldAt: Long,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    val pseudonym: String?,
    @ColumnInfo(name = "age_band") val ageBand: String?,
    val sex: String?,
    @ColumnInfo(name = "sync_state") val syncState: SyncState = SyncState.PENDING,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "sync_message") val syncMessage: String? = null,
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["client_uuid"],
            childColumns = ["sale_client_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sale_client_uuid")],
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sale_client_uuid") val saleClientUuid: String,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Int,
    @ColumnInfo(name = "unit_price_cents") val unitPriceCents: Long,
)

@Entity(tableName = "claims", indices = [Index("sync_state"), Index("sale_client_uuid")])
data class ClaimEntity(
    @PrimaryKey @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "sale_client_uuid") val saleClientUuid: String?,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Int,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "test_result") val testResult: String?,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long,
    val status: String = "submitted",
    val reference: String? = null,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null,
    @ColumnInfo(name = "sync_state") val syncState: SyncState = SyncState.PENDING,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "sync_message") val syncMessage: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
)

@Entity(tableName = "stock_adjustments", indices = [Index("sync_state")])
data class StockAdjustmentEntity(
    @PrimaryKey @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "product_id") val productId: Long,
    val delta: Int,
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: SyncState = SyncState.PENDING,
    @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @ColumnInfo(name = "sync_message") val syncMessage: String? = null,
)

/* ---- Read models ---- */

data class StockRow(
    @Embedded val product: ProductEntity,
    @ColumnInfo(name = "quantity_on_hand") val quantityOnHand: Int?,
    @ColumnInfo(name = "reorder_level") val reorderLevel: Int?,
    @ColumnInfo(name = "pending_adjustments") val pendingAdjustments: Int,
)

data class SaleRow(
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "sold_at") val soldAt: Long,
    @ColumnInfo(name = "total_cents") val totalCents: Long,
    @ColumnInfo(name = "sync_state") val syncState: SyncState,
    @ColumnInfo(name = "sync_message") val syncMessage: String?,
    @ColumnInfo(name = "items_label") val itemsLabel: String?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "claim_count") val claimCount: Int,
)

data class ClaimRow(
    @Embedded val claim: ClaimEntity,
    @ColumnInfo(name = "product_name") val productName: String?,
)

data class AdjustmentRow(
    @Embedded val adjustment: StockAdjustmentEntity,
    @ColumnInfo(name = "product_name") val productName: String?,
)
