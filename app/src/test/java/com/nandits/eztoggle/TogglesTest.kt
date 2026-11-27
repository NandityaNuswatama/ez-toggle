package com.nandits.eztoggle

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSettings(
    override var wifiOn: Boolean = false,
    override var mobileDataOn: Boolean? = false,
    override var locationOn: Boolean = false,
    override var airplaneOn: Boolean = false,
    override var dndOn: Boolean = false,
    override var dndAccess: Boolean = true,
    override var phonePermission: Boolean = true,
) : DeviceSettings {
    override fun setDnd(on: Boolean) { dndOn = on }
}

class SettingsStateReaderTest {
    @Test fun mapsBooleansToOnOff() {
        val states = SettingsStateReader(FakeSettings(wifiOn = true, locationOn = false, airplaneOn = true, dndOn = true)).all()
        assertEquals(ToggleState.On, states[ToggleType.WIFI])
        assertEquals(ToggleState.Off, states[ToggleType.LOCATION])
        assertEquals(ToggleState.On, states[ToggleType.AIRPLANE])
        assertEquals(ToggleState.On, states[ToggleType.DND])
        assertEquals(ToggleType.entries.toSet(), states.keys)
    }

    @Test fun unreadableMobileDataIsUnknown() {
        assertEquals(ToggleState.Unknown, SettingsStateReader(FakeSettings(mobileDataOn = null)).state(ToggleType.MOBILE_DATA))
    }

    @Test fun dndWithoutAccessNeedsPermission() {
        assertEquals(ToggleState.PermissionRequired, SettingsStateReader(FakeSettings(dndAccess = false)).state(ToggleType.DND))
    }
}

class ToggleActionHandlerTest {
    private fun open(type: ToggleType, s: FakeSettings = FakeSettings()) =
        (ToggleActionHandler(s).resolve(type) as ToggleAction.Open).intentAction

    @Test fun panelsAndSettingsPerType() {
        assertEquals(Settings.Panel.ACTION_WIFI, open(ToggleType.WIFI))
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, open(ToggleType.MOBILE_DATA))
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, open(ToggleType.LOCATION))
        assertEquals(Settings.ACTION_AIRPLANE_MODE_SETTINGS, open(ToggleType.AIRPLANE))
    }

    @Test fun dndTogglesDirectlyWithAccess() {
        assertEquals(ToggleAction.SetDnd(true), ToggleActionHandler(FakeSettings(dndOn = false)).resolve(ToggleType.DND))
        assertEquals(ToggleAction.SetDnd(false), ToggleActionHandler(FakeSettings(dndOn = true)).resolve(ToggleType.DND))
    }

    @Test fun dndWithoutAccessOpensGrantScreen() {
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, open(ToggleType.DND, FakeSettings(dndAccess = false)))
    }

    @Test fun applySetsDndAndReturnsNoIntent() {
        val s = FakeSettings()
        assertNull(s.apply(ToggleAction.SetDnd(true)))
        assertTrue(s.dndOn)
    }
}

class KeyguardGuardTest {
    private fun run(locked: Boolean, unlocks: Boolean): Triple<Boolean, Boolean, Boolean> {
        var asked = false; var ran = false; var aborted = false
        KeyguardGuard.guard(locked, { asked = true; it(unlocks) }, { ran = true }, { aborted = true })
        return Triple(asked, ran, aborted)
    }

    @Test fun unlockedRunsWithoutAsking() = assertEquals(Triple(false, true, false), run(locked = false, unlocks = false))
    @Test fun lockedRunsOnlyAfterDismiss() = assertEquals(Triple(true, true, false), run(locked = true, unlocks = true))
    @Test fun lockedAndDismissFailsAborts() = assertEquals(Triple(true, false, true), run(locked = true, unlocks = false))
}

class ControlViewModelTest {
    @Test fun refreshPublishesState() {
        val vm = ControlViewModel(FakeSettings(wifiOn = true, dndAccess = false, phonePermission = false))
        vm.onIntent(ControlIntent.Refresh)
        val s = vm.state.value
        assertEquals(ToggleState.On, s.states[ToggleType.WIFI])
        assertFalse(s.dndAccess)
        assertFalse(s.phonePermission)
    }

    @Test fun tapPanelEmitsLaunch() {
        val vm = ControlViewModel(FakeSettings())
        vm.onIntent(ControlIntent.Tap(ToggleType.WIFI))
        assertEquals(ControlEffect.Launch(Settings.Panel.ACTION_WIFI), vm.effects.tryReceive().getOrNull())
    }

    @Test fun tapDndTogglesAndUpdatesStateWithoutEffect() {
        val vm = ControlViewModel(FakeSettings())
        vm.onIntent(ControlIntent.Tap(ToggleType.DND))
        assertEquals(ToggleState.On, vm.state.value.states[ToggleType.DND])
        assertNull(vm.effects.tryReceive().getOrNull())
    }

    @Test fun grantIntentsEmitEffects() {
        val vm = ControlViewModel(FakeSettings())
        vm.onIntent(ControlIntent.GrantDnd)
        vm.onIntent(ControlIntent.GrantPhoneState)
        assertEquals(ControlEffect.Launch(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), vm.effects.tryReceive().getOrNull())
        assertEquals(ControlEffect.RequestPhonePermission, vm.effects.tryReceive().getOrNull())
    }
}
