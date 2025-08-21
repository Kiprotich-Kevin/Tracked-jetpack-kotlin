package com.yubytech.tracked.api

import androidx.lifecycle.ViewModelProvider
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface WeatherApi {
    @GET("/v1/current.json")
    suspend fun getWeather(
        @Query("key") apiKey : String,
        @Query("q") city : String,
    ) : Response<WeatherModel>
}