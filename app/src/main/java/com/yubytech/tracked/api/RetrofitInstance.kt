package com.yubytech.tracked.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.yubytech.tracked.ui.SharedPrefsUtils

object RetrofitInstance {
    private const val baseUrl = "https://api.brisk-credit.net"
    
    private fun getInstance(context: Context? = null) : Retrofit {
        val clientBuilder = OkHttpClient.Builder()
        
        // Add JWT token interceptor if context is provided
        context?.let { ctx ->
            clientBuilder.addInterceptor { chain ->
                val jwtToken = SharedPrefsUtils.getJwtToken(ctx)
                val request = chain.request()
                
                val newRequest = if (jwtToken != null) {
                    request.newBuilder()
                        .addHeader("Authorization", "Bearer $jwtToken")
                        .build()
                } else {
                    request
                }
                
                chain.proceed(newRequest)
            }
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val weatherApi : WeatherApi = getInstance().create(WeatherApi::class.java)
    val clientsApi: ClientsApi = getInstance().create(ClientsApi::class.java)
    
    // Function to get API instances with JWT token support
    fun getClientsApiWithAuth(context: Context): ClientsApi {
        return getInstance(context).create(ClientsApi::class.java)
    }
    
    fun getWeatherApiWithAuth(context: Context): WeatherApi {
        return getInstance(context).create(WeatherApi::class.java)
    }
}