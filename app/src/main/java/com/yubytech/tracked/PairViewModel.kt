package com.yubytech.tracked

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yubytech.tracked.api.GetPairsRequest
import com.yubytech.tracked.api.GetPairsResponse
import com.yubytech.tracked.api.PairItem
import com.yubytech.tracked.api.RetrofitInstance
import kotlinx.coroutines.launch

class PairViewModel : ViewModel() {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var yourPair by mutableStateOf<PairItem?>(null)
    var createdPairs by mutableStateOf<List<PairItem>>(emptyList())

    fun fetchPairs(context: Context, userId: Int) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val api = RetrofitInstance.getPairsApiWithAuth(context)
                val response = api.getPairs(GetPairsRequest(userId))
                if (response.isSuccessful) {
                    val body: GetPairsResponse? = response.body()
                    if (body?.success == true) {
                        yourPair = body.your_pair
                        createdPairs = body.created_pairs ?: emptyList()
                    } else {
                        error = body?.message ?: "Failed to load pairs"
                    }
                } else {
                    error = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Network error"
            } finally {
                loading = false
            }
        }
    }
}


