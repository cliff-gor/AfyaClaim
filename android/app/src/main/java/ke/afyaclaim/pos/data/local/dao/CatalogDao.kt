package ke.afyaclaim.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ke.afyaclaim.pos.data.local.entity.InventoryEntity
import ke.afyaclaim.pos.data.local.entity.ProductEntity
import ke.afyaclaim.pos.data.local.entity.StockRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Upsert
    suspend fun upsertInventory(items: List<InventoryEntity>)

    @Query("SELECT * FROM products ORDER BY category, name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory WHERE product_id = :productId")
    suspend fun inventoryFor(productId: Long): InventoryEntity?

    @Query("SELECT * FROM inventory")
    suspend fun allInventory(): List<InventoryEntity>

    @Query("UPDATE inventory SET quantity_on_hand = quantity_on_hand + :delta WHERE product_id = :productId")
    suspend fun applyDelta(productId: Long, delta: Int): Int

    @Query(
        """
        SELECT p.*, i.quantity_on_hand, i.reorder_level,
               (SELECT COUNT(*) FROM stock_adjustments a
                 WHERE a.product_id = p.id AND a.sync_state = 'pending') AS pending_adjustments
        FROM products p
        LEFT JOIN inventory i ON i.product_id = p.id
        ORDER BY p.category, p.name
        """,
    )
    fun observeStock(): Flow<List<StockRow>>
}
