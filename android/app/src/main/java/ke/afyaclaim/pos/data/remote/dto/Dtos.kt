package ke.afyaclaim.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val name: String,
    val role: String,
    val pharmacy: PharmacyDto? = null,
)

@Serializable
data class PharmacyDto(val id: Long, val name: String, val town: String? = null)

@Serializable
data class ProductDto(
    val id: Long,
    val sku: String,
    val name: String,
    val category: String,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
    @SerialName("subsidy_cents") val subsidyCents: Long = 0,
    val subsidised: Boolean = false,
)

@Serializable
data class ProductsResponse(val data: List<ProductDto>)

@Serializable
data class InventoryDto(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("quantity_on_hand") val quantityOnHand: Int,
    @SerialName("reorder_level") val reorderLevel: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ClaimStatusDto(
    val id: Long,
    @SerialName("client_uuid") val clientUuid: String? = null,
    val reference: String? = null,
    @SerialName("product_id") val productId: Long,
    val quantity: Int,
    @SerialName("amount_cents") val amountCents: Long,
    val status: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("test_result") val testResult: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PatientDto(
    val pseudonym: String?,
    @SerialName("age_band") val ageBand: String,
    val sex: String,
)

@Serializable
data class SaleItemDto(
    @SerialName("product_id") val productId: Long,
    val quantity: Int,
    @SerialName("unit_price_cents") val unitPriceCents: Long,
)

@Serializable
data class SalePushDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("sold_at") val soldAt: String,
    val patient: PatientDto?,
    @SerialName("total_cents") val totalCents: Long,
    val items: List<SaleItemDto>,
)

@Serializable
data class ClaimPushDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("sale_client_uuid") val saleClientUuid: String?,
    @SerialName("product_id") val productId: Long,
    val quantity: Int,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("test_result") val testResult: String?,
    @SerialName("submitted_at") val submittedAt: String,
)

@Serializable
data class StockAdjustmentPushDto(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("product_id") val productId: Long,
    val delta: Int,
    val reason: String,
)

@Serializable
data class SyncPushRequest(
    val sales: List<SalePushDto>,
    val claims: List<ClaimPushDto>,
    @SerialName("stock_adjustments") val stockAdjustments: List<StockAdjustmentPushDto>,
)

@Serializable
data class SyncResultDto(
    val type: String,
    @SerialName("client_uuid") val clientUuid: String,
    val status: String,
    @SerialName("server_id") val serverId: Long? = null,
    val message: String? = null,
)

@Serializable
data class SyncPushResponse(val results: List<SyncResultDto> = emptyList())

@Serializable
data class SyncPullResponse(
    val products: List<ProductDto> = emptyList(),
    val inventory: List<InventoryDto> = emptyList(),
    val claims: List<ClaimStatusDto> = emptyList(),
    val cursor: String? = null,
)

@Serializable
data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
data class ErrorBody(val code: String? = null, val message: String? = null)
