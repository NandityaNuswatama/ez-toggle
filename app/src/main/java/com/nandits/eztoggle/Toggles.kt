package com.nandits.eztoggle

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class ToggleType(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    WIFI(R.string.toggle_wifi, R.drawable.ic_wifi),
    MOBILE_DATA(R.string.toggle_mobile_data, R.drawable.ic_mobile_data),
    LOCATION(R.string.toggle_location, R.drawable.ic_location),
    AIRPLANE(R.string.toggle_airplane, R.drawable.ic_airplane),
    DND(R.string.toggle_dnd, R.drawable.ic_dnd);

    /** Everything except DND hands off to system UI. */
    val opensSettings get() = this != DND
}

enum class ToggleState(@param:StringRes val label: Int) {
    On(R.string.toggle_state_on),
    Off(R.string.toggle_state_off),
    Unknown(R.string.toggle_state_unknown),
    PermissionRequired(R.string.toggle_state_permission_required),
}

sealed interface ToggleAction {
    /** Launch system UI by intent action string (kept as a String so it is JVM-testable). */
    data class Open(val intentAction: String) : ToggleAction
    data class SetDnd(val on: Boolean) : ToggleAction
}

/** The system reads/writes the app needs. Faked in unit tests. */
interface DeviceSettings {
    val wifiOn: Boolean
    /** null when the platform refuses to tell us (no READ_PHONE_STATE). */
    val mobileDataOn: Boolean?
    val locationOn: Boolean
    val airplaneOn: Boolean
    val dndOn: Boolean
    val dndAccess: Boolean
    val phonePermission: Boolean
    fun setDnd(on: Boolean)
}

class AndroidDeviceSettings(context: Context) : DeviceSettings {
    private val app = context.applicationContext
    private val notifications = app.getSystemService(NotificationManager::class.java)

    override val wifiOn get() = app.getSystemService(WifiManager::class.java).isWifiEnabled
    override val mobileDataOn: Boolean?
        get() = try {
            app.getSystemService(TelephonyManager::class.java).isDataEnabled
        } catch (_: SecurityException) {
            null
        }
    override val locationOn get() = app.getSystemService(LocationManager::class.java).isLocationEnabled
    override val airplaneOn
        get() = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    override val dndOn
        get() = notifications.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
    override val dndAccess get() = notifications.isNotificationPolicyAccessGranted
    override val phonePermission
        get() = app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    // On API 35+ this may surface as an app-owned Mode rather than global DND (PRD §5); verify on device.
    override fun setDnd(on: Boolean) = notifications.setInterruptionFilter(
        if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
    )
}

class SettingsStateReader(private val settings: DeviceSettings) {
    fun state(type: ToggleType): ToggleState = when (type) {
        ToggleType.WIFI -> settings.wifiOn.toState()
        ToggleType.MOBILE_DATA -> settings.mobileDataOn?.toState() ?: ToggleState.Unknown
        ToggleType.LOCATION -> settings.locationOn.toState()
        ToggleType.AIRPLANE -> settings.airplaneOn.toState()
        ToggleType.DND -> if (settings.dndAccess) settings.dndOn.toState() else ToggleState.PermissionRequired
    }

    fun all(): Map<ToggleType, ToggleState> = ToggleType.entries.associateWith(::state)

    private fun Boolean.toState() = if (this) ToggleState.On else ToggleState.Off
}

class ToggleActionHandler(private val settings: DeviceSettings) {
    // ponytail: location always opens settings; add the Play Services SettingsClient dialog for "turn on" if the extra tap annoys.
    fun resolve(type: ToggleType): ToggleAction = when (type) {
        ToggleType.WIFI -> ToggleAction.Open(Settings.Panel.ACTION_WIFI)
        ToggleType.MOBILE_DATA -> ToggleAction.Open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        ToggleType.LOCATION -> ToggleAction.Open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        ToggleType.AIRPLANE -> ToggleAction.Open(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        ToggleType.DND ->
            if (settings.dndAccess) ToggleAction.SetDnd(!settings.dndOn)
            else ToggleAction.Open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }
}

/** Runs a resolved action. Returns the intent to launch, or null if it was applied directly. */
fun DeviceSettings.apply(action: ToggleAction): String? = when (action) {
    is ToggleAction.Open -> action.intentAction
    is ToggleAction.SetDnd -> { setDnd(action.on); null }
}
