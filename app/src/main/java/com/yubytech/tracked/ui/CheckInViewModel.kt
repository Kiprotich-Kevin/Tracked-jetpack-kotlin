package com.yubytech.tracked.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yubytech.tracked.local.ActivityEvent
import com.yubytech.tracked.local.ActivityEventSyncManager
import com.yubytech.tracked.local.AppDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class CheckInUiState {
    object Idle : CheckInUiState()
    object GettingLocation : CheckInUiState()
    data class ShowClientDialog(val location: Location) : CheckInUiState()
    data class ShowCheckoutDialog(val location: Location) : CheckInUiState()
    data class Progress(val message: String) : CheckInUiState()
    data class Error(val message: String) : CheckInUiState()
    object Success : CheckInUiState()
    object PermissionRequired : CheckInUiState()
    object LocationRequired : CheckInUiState()
    object LocationFailed : CheckInUiState()
}

data class CheckInStatus(
    val isCheckedIn: Boolean = false,
    val clientId: String? = null,
    val clientName: String? = null,
    val clientContact: Long? = null,
    val clientLoanOfficer: String? = null,
    val checkInLat: Double? = null,
    val checkInLon: Double? = null
)

class CheckInViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext

    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Idle)
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val _status = MutableStateFlow(CheckInStatus())
    val status: StateFlow<CheckInStatus> = _status.asStateFlow()

    init {
        loadStatus()
    }

    fun loadStatus() {
        _status.value = CheckInStatus(
            isCheckedIn = CheckInPrefs.isCheckedIn(context),
            clientId = CheckInPrefs.getClientId(context),
            clientName = CheckInPrefs.getClientName(context),
            clientContact = CheckInPrefs.getClientContact(context),
            clientLoanOfficer = CheckInPrefs.getClientLoanOfficer(context),
            checkInLat = CheckInPrefs.getCheckInLat(context),
            checkInLon = CheckInPrefs.getCheckInLon(context)
        )
    }

    fun swipeAction() {
        if (!PermissionUtils.hasAllLocationPermissions(context)) {
            _uiState.value = CheckInUiState.PermissionRequired
            return
        }
        if (_status.value.isCheckedIn) {
            // Start checkout flow
            getLocationForCheckout()
        } else {
            // Start check-in flow
            getLocationForCheckIn()
        }
    }

    private fun getLocationForCheckIn() {
        if (!PermissionUtils.isLocationEnabled(context)) {
            _uiState.value = CheckInUiState.LocationRequired
            return
        }
        _uiState.value = CheckInUiState.GettingLocation
        viewModelScope.launch {
            val location = withTimeoutOrNull(20_000) {
                var result: Location? = null
                while (result == null) {
                    result = LocationUtils.getAccurateLocation(context)
                }
                result
            }
            if (location != null) {
                _uiState.value = CheckInUiState.ShowClientDialog(location)
            } else {
                _uiState.value = CheckInUiState.LocationFailed
            }
        }
    }

    fun onClientSelected(client: Client, location: Location) {
        // Save check-in
        CheckInPrefs.setCheckIn(
            context,
            clientDbId = client.id,
            clientId = client.idno.toString(),
            clientName = client.name,
            clientContact = client.contact,
            clientLoanOfficer = client.loan_officer,
            lat = location.latitude,
            lon = location.longitude
        )
        // Log check-in event
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
        val event = ActivityEvent(
            user_id = userId.toIntOrNull() ?: -1,
            event_type = "checkin",
            event_time = now,
            lat = location.latitude ?: 0.0,
            lng = location.longitude ?: 0.0,
            details = "User checked in to client",
            session_id = 0,
            client_id = client.id ?: 0
        )
        GlobalScope.launch {
            val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
            val posted = ActivityEventSyncManager.isInternetAvailable(context) &&
                ActivityEventSyncManager.postEventOnline(event, endpoint, context)
            if (!posted) {
                val db = AppDatabase.getDatabase(context)
                db.activityEventDao().insert(event)
            }
        }
        loadStatus()
        _uiState.value = CheckInUiState.Success
    }

    private fun getLocationForCheckout() {
        if (!PermissionUtils.isLocationEnabled(context)) {
            _uiState.value = CheckInUiState.LocationRequired
            return
        }
        _uiState.value = CheckInUiState.GettingLocation
        viewModelScope.launch {
            val location = withTimeoutOrNull(20_000) {
                var result: Location? = null
                while (result == null) {
                    result = LocationUtils.getAccurateLocation(context)
                }
                result
            }
            if (location != null) {
                _uiState.value = CheckInUiState.ShowCheckoutDialog(location)
            } else {
                _uiState.value = CheckInUiState.LocationFailed
            }
        }
    }

    fun onCheckoutSubmit(location: Location) {
        _uiState.value = CheckInUiState.Progress("Checking out...")
        viewModelScope.launch {
            val checkInLat = CheckInPrefs.getCheckInLat(context)
            val checkInLon = CheckInPrefs.getCheckInLon(context)
            if (checkInLat != null && checkInLon != null) {
                val distance = LocationUtils.haversine(
                    checkInLat, checkInLon,
                    location.latitude, location.longitude
                )
                if (distance <= 100.0) {
                    // Success
                    CheckInPrefs.setCheckOut(context, location.latitude, location.longitude)
                    // Log checkout event
                    val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    val clientId = CheckInPrefs.getClientDbId(context)
                    val userId = SharedPrefsUtils.getUserIdFromPrefs(context)
                    val checkoutEvent = ActivityEvent(
                        user_id = userId.toIntOrNull() ?: -1,
                        event_type = "checkout",
                        event_time = now,
                        lat = location.latitude ?: 0.0,
                        lng = location.longitude ?: 0.0,
                        details = "User checked out from client",
                        session_id = 0,
                        client_id = clientId ?: 0
                    )
                    GlobalScope.launch {
                        val endpoint = "https://api.brisk-credit.net/endpoints/activity_logs.php"
                        val posted = ActivityEventSyncManager.isInternetAvailable(context) &&
                            ActivityEventSyncManager.postEventOnline(checkoutEvent, endpoint, context)
                        if (!posted) {
                            val db = AppDatabase.getDatabase(context)
                            db.activityEventDao().insert(checkoutEvent)
                        }
                    }
                    loadStatus()
                    _uiState.value = CheckInUiState.Success
                } else {
                    _uiState.value = CheckInUiState.Error("Failed: Move to the client's location to checkout.")
                }
            } else {
                _uiState.value = CheckInUiState.Error("No check-in location found.")
            }
        }
    }

    fun clearUiState() {
        _uiState.value = CheckInUiState.Idle
    }

    fun clearAll() {
        CheckInPrefs.clear(context)
        loadStatus()
        _uiState.value = CheckInUiState.Idle
    }

    fun retryLocation(isCheckIn: Boolean) {
        if (isCheckIn) getLocationForCheckIn() else getLocationForCheckout()
    }
} 