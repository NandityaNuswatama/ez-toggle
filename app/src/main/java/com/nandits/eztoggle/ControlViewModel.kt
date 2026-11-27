package com.nandits.eztoggle

import android.provider.Settings
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ControlUiState(
    val states: Map<ToggleType, ToggleState> = emptyMap(),
    val dndAccess: Boolean = true,
    val phonePermission: Boolean = true,
)

sealed interface ControlIntent {
    data object Refresh : ControlIntent
    data class Tap(val type: ToggleType) : ControlIntent
    data object GrantDnd : ControlIntent
    data object GrantPhoneState : ControlIntent
}

sealed interface ControlEffect {
    data class Launch(val intentAction: String) : ControlEffect
    data object RequestPhonePermission : ControlEffect
}

/** Callers must pass [ControlIntent.Tap] through [KeyguardGuard] first. */
class ControlViewModel(private val settings: DeviceSettings) : ViewModel() {
    private val reader = SettingsStateReader(settings)
    private val handler = ToggleActionHandler(settings)

    private val _state = MutableStateFlow(ControlUiState())
    val state: StateFlow<ControlUiState> = _state.asStateFlow()

    private val _effects = Channel<ControlEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<ControlEffect> = _effects

    fun onIntent(intent: ControlIntent) {
        when (intent) {
            ControlIntent.Refresh -> Unit
            is ControlIntent.Tap ->
                settings.apply(handler.resolve(intent.type))?.let { _effects.trySend(ControlEffect.Launch(it)) }
            ControlIntent.GrantDnd ->
                _effects.trySend(ControlEffect.Launch(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            ControlIntent.GrantPhoneState -> _effects.trySend(ControlEffect.RequestPhonePermission)
        }
        _state.value = ControlUiState(reader.all(), settings.dndAccess, settings.phonePermission)
    }
}
