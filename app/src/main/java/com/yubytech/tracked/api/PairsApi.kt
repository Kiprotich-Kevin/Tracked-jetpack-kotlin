// File: PairsApi.kt
package com.yubytech.tracked.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class GetPairsRequest(
    val user_id: Int
)

data class PairItem(
    val id: Int,
    val p_1: Int,
    val staff1_name: String,
    val p_2: Int,
    val staff2_name: String,
    val active: Int,
    val created_by: Int?,
    val created_by_name: String?,
    val created_at: String,
    val updated_at: String
)

data class GetPairsResponse(
    val success: Boolean,
    val message: String?,
    val your_pair: PairItem?,
    val created_pairs_count: Int?,
    val created_pairs: List<PairItem>?
)

interface PairsApi {
    @POST("get_pairs.php")
    suspend fun getPairs(@Body body: GetPairsRequest): Response<GetPairsResponse>
}


