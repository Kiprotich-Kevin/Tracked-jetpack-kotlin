package com.yubytech.tracked

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yubytech.tracked.api.Constant
import com.yubytech.tracked.api.RetrofitInstance
import com.yubytech.tracked.ui.Client
import kotlinx.coroutines.launch

class WeatherViewModel :ViewModel() {

    private val weatherApi = RetrofitInstance.weatherApi
    private val _clients = mutableStateListOf<Client>()
    val clients: List<Client> get() = _clients

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun getData(city:String){
        viewModelScope.launch {
            val response = weatherApi.getWeather(Constant.apiKey,city)
            if (response.isSuccessful) {
                Log.i("Response : ", response.body().toString())
            }else{
                Log.i("Error : ", response.message().toString())
            }
        }
    }

    fun fetchClientsByUserId(userId: String, context: Context) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getClientsByUserId(userId)
                if (response.isSuccessful) {
                    _clients.clear()
                    response.body()?.let { _clients.addAll(it) }
                } else {
                    error = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                error = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                loading = false
            }
        }
    }
}