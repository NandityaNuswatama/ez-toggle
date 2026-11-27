package com.nandits.eztoggle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nandits.eztoggle.ui.ControlScreen
import com.nandits.eztoggle.ui.theme.EztoggleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ControlScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: ControlUiState, onIntent: (ControlIntent) -> Unit = {}) = rule.setContent {
        EztoggleTheme { ControlScreen(state, onIntent, onOpenChecklist = {}) }
    }

    @Test fun rendersEachState() {
        show(
            ControlUiState(
                states = mapOf(
                    ToggleType.WIFI to ToggleState.On,
                    ToggleType.LOCATION to ToggleState.Off,
                    ToggleType.MOBILE_DATA to ToggleState.Unknown,
                    ToggleType.DND to ToggleState.PermissionRequired,
                ),
            ),
        )
        listOf("On", "Off", "Unknown", "Needs access").forEach { rule.onAllNodesWithText(it, useUnmergedTree = true).onFirst().assertIsDisplayed() }
    }

    @Test fun bannersShowOnlyWhenNeeded() {
        show(ControlUiState(states = mapOf(ToggleType.MOBILE_DATA to ToggleState.Unknown), dndAccess = false, phonePermission = false))
        rule.onNodeWithText("Grant Do Not Disturb access", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Grant phone access", substring = true).assertIsDisplayed()
    }

    @Test fun noBannersWhenGranted() {
        show(ControlUiState(states = mapOf(ToggleType.MOBILE_DATA to ToggleState.On)))
        rule.onNodeWithText("Grant Do Not Disturb access", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Grant phone access", substring = true).assertDoesNotExist()
    }

    @Test fun tapSendsIntent() {
        var sent: ControlIntent? = null
        show(ControlUiState()) { sent = it }
        rule.onNodeWithText("Wi-Fi").performClick()
        assertEquals(ControlIntent.Tap(ToggleType.WIFI), sent)
    }
}

