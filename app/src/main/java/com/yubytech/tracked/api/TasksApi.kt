// File: TasksApi.kt
package com.yubytech.tracked.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Task data models
data class TaskResponse(
    val success: Boolean,
    val tasks: List<Task>
)

data class Task(
    val id: Int,
    val title: String,
    val description: String,
    val priority: String,
    val client_id: Int,
    val status: String,
    val due_date: String,
    val created_at: String,
    val created_by_name: String,
    val client_name: String,
    val client_contact: String,
)

// API interface for tasks
interface TasksApi {
    @GET("get_tasks.php")
    suspend fun getTasks(@Query("user_id") userId: String): Response<TaskResponse>
}