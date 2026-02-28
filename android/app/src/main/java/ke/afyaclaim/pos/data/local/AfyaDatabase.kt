package ke.afyaclaim.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ke.afyaclaim.pos.data.local.dao.CatalogDao
import ke.afyaclaim.pos.data.local.dao.ClaimDao
import ke.afyaclaim.pos.data.local.dao.SaleDao
import ke.afyaclaim.pos.data.local.dao.StockAdjustmentDao
import ke.afyaclaim.pos.data.local.entity.ClaimEntity
import ke.afyaclaim.pos.data.local.entity.InventoryEntity
import ke.afyaclaim.pos.data.local.entity.ProductEntity
import ke.afyaclaim.pos.data.local.entity.SaleEntity
import ke.afyaclaim.pos.data.local.entity.SaleItemEntity
import ke.afyaclaim.pos.data.local.entity.StockAdjustmentEntity

@Database(
    entities = [
        ProductEntity::class,
        InventoryEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ClaimEntity::class,
        StockAdjustmentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AfyaDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun saleDao(): SaleDao
    abstract fun claimDao(): ClaimDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao

    companion object {
        const val NAME = "afyaclaim.db"
    }
}
