package com.yubytech.tracked

// File: TaskViewModel.kt
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yubytech.tracked.api.HierarchyUser
import com.yubytech.tracked.api.RetrofitInstance
import com.yubytech.tracked.api.UserActivityEvent
import com.yubytech.tracked.api.Task
import com.yubytech.tracked.ui.Client
import kotlinx.coroutines.launch

data class User(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isActive: Boolean = false,
    val isYou: Boolean = false
)

class TaskViewModel : ViewModel() {
    // Users state
    var users by mutableStateOf<List<HierarchyUser>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Timeline state (keeping for backward compatibility)
    var timeline by mutableStateOf<List<UserActivityEvent>>(emptyList())
    var timelineLoading by mutableStateOf(false)
    var timelineError by mutableStateOf<String?>(null)

    // Tasks state
    var tasks by mutableStateOf<List<Task>>(emptyList())
    var tasksLoading by mutableStateOf(false)
    var tasksError by mutableStateOf<String?>(null)

    // Clients state
    var clients by mutableStateOf<List<Client>>(emptyList())
    var clientsLoading by mutableStateOf(false)
    var clientsError by mutableStateOf<String?>(null)

    fun fetchUsers(userId: String, context: Context) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getHierarchyUsers(userId)
                if (response.isSuccessful) {
                    users = response.body() ?: emptyList()
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

    fun fetchTimeline(userId: String, context: Context) {
        viewModelScope.launch {
            timelineLoading = true
            timelineError = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getUserActivity(userId)
                if (response.isSuccessful) {
                    timeline = response.body() ?: emptyList()
                } else {
                    timelineError = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                timelineError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                timelineLoading = false
            }
        }
    }

    fun fetchTasks(userId: String, context: Context) {
        viewModelScope.launch {
            tasksLoading = true
            tasksError = null
            try {
                // You'll need to add this method to your RetrofitInstance
                val response = RetrofitInstance.getTasksApiWithAuth(context).getTasks(userId)
                if (response.isSuccessful) {
                    tasks = response.body()?.tasks ?: emptyList()
                } else {
                    tasksError = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                tasksError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                tasksLoading = false
            }
        }
    }

    fun fetchClients(userId: String, context: Context) {
        viewModelScope.launch {
            clientsLoading = true
            clientsError = null
            try {
                val response = RetrofitInstance.getClientsApiWithAuth(context).getClientsByUserId(userId)
                if (response.isSuccessful) {
                    clients = response.body() ?: emptyList()
                } else {
                    clientsError = "Server error: ${response.message()}"
                }
            } catch (e: Exception) {
                clientsError = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                clientsLoading = false
            }
        }
    }

    
}