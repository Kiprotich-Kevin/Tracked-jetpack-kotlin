package com.yubytech.tracked.api

import android.annotation.SuppressLint
import androidx.webkit.internal.ApiFeature
// T refers to weather model
sealed class NetworkResponse<out T> {
    data class Success<out T>(val data : T) : NetworkResponse<T>()
    data class Error(val message : String) : NetworkResponse<Nothing>()
    object Loading : NetworkResponse<Nothing>()
}