package ke.afyaclaim.pos.data.remote

import ke.afyaclaim.pos.data.remote.dto.LoginRequest
import ke.afyaclaim.pos.data.remote.dto.LoginResponse
import ke.afyaclaim.pos.data.remote.dto.ProductsResponse
import ke.afyaclaim.pos.data.remote.dto.SyncPullResponse
import ke.afyaclaim.pos.data.remote.dto.SyncPushRequest
import ke.afyaclaim.pos.data.remote.dto.SyncPushResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AfyaApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("products")
    suspend fun products(): ProductsResponse

    @POST("sync")
    suspend fun push(@Body body: SyncPushRequest): SyncPushResponse

    @GET("sync")
    suspend fun pull(@Query("since") since: String?): SyncPullResponse
}
