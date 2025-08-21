package com.yubytech.tracked.api

import com.yubytech.tracked.ui.Client
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PartMap

data class HierarchyUser(
    val id: Int,
    val name: String,
    val contact: Long,
    val tr_status: Int
)

data class UserActivityEvent(
    val id: Int,
    val user_id: Int,
    val session_id: Int?,
    val event_type: String,
    val event_time: String,
    val lat: String?,
    val lng: String?,
    val details: String?,
    val client_id: Int?,
    val client_name: String?,
    val client_co: Int?
)

data class InteractionRequest(
    val client: Int,
    val comment: String,
    val source: Int,
    val time: Int,
    val type: String,
    val tracked: Int,
    val imagepath: List<String>
)

interface ClientsApi {
    @GET("/endpoints/clients.php")
    suspend fun getClientsByUserId(
        @Query("user_id") userId: String
    ): Response<List<Client>>

    @GET("/endpoints/hierachy.php")
    suspend fun getHierarchyUsers(
        @Query("user_id") userId: String
    ): Response<List<HierarchyUser>>

    @GET("/endpoints/get_user_activity.php")
    suspend fun getUserActivity(
        @Query("user_id") userId: String
    ): Response<List<UserActivityEvent>>

    @GET("/endpoints/get_user_activity.php")
    suspend fun getUserActivityDashboard(
        @Query("user_id") userId: String
    ): Response<List<UserActivityEvent>>

    @POST("/endpoints/interactions.php")
    suspend fun postInteraction(
        @Body request: InteractionRequest
    ): Response<Unit>

    @Multipart
    @POST("/endpoints/interactions.php")
    suspend fun postInteractionMultipart(
        @PartMap data: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part images: List<MultipartBody.Part>
    ): Response<Unit>
} 