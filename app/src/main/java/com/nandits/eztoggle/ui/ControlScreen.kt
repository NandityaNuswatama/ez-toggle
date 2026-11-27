package com.nandits.eztoggle.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.nandits.eztoggle.ControlIntent
import com.nandits.eztoggle.ControlUiState
import com.nandits.eztoggle.R
import com.nandits.eztoggle.ToggleState
import com.nandits.eztoggle.ToggleType
import com.nandits.eztoggle.ui.theme.EztoggleTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    state: ControlUiState,
    onIntent: (ControlIntent) -> Unit,
    onOpenChecklist: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.checklist_title)) },
                                onClick = { menuOpen = false; onOpenChecklist() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                onClick = { menuOpen = false; aboutOpen = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            if (!state.dndAccess) item {
                Banner(stringResource(R.string.banner_dnd), stringResource(R.string.grant)) { onIntent(ControlIntent.GrantDnd) }
            }
            if (!state.phonePermission && state.states[ToggleType.MOBILE_DATA] == ToggleState.Unknown) item {
                Banner(stringResource(R.string.banner_phone), stringResource(R.string.grant)) {
                    onIntent(ControlIntent.GrantPhoneState)
                }
            }
            items(ToggleType.entries) { type ->
                ToggleCard(type, state.states[type] ?: ToggleState.Unknown) { onIntent(ControlIntent.Tap(type)) }
            }
        }
    }
    if (aboutOpen) AlertDialog(
        onDismissRequest = { aboutOpen = false },
        confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text(stringResource(R.string.about_body)) },
    )
}

@Composable
fun ToggleCard(type: ToggleType, state: ToggleState, onClick: () -> Unit) {
    val on = state == ToggleState.On
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.Dp>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>()
    // Shape morph: On = extra-large rounding (28dp token), Off = medium (12dp token).
    val corner by animateDpAsState(if (on) 28.dp else 12.dp, spatial, label = "corner")
    val container by animateColorAsState(if (on) colors.primaryContainer else colors.surface, effects, label = "container")
    val content by animateColorAsState(if (on) colors.onPrimaryContainer else colors.onSurface, effects, label = "content")
    Surface(
        onClick = {
            haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onClick()
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        border = if (on) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Icon(painterResource(type.icon), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(state.label), style = MaterialTheme.typography.bodyMedium)
            }
            if (type.opensSettings) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.opens_settings))
            }
        }
    }
}

@Composable
fun Banner(text: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@PreviewLightDark
@Composable
private fun ToggleCardStatesPreview() = EztoggleTheme {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleCard(ToggleType.WIFI, ToggleState.On) {}
            ToggleCard(ToggleType.LOCATION, ToggleState.Off) {}
            ToggleCard(ToggleType.MOBILE_DATA, ToggleState.Unknown) {}
            ToggleCard(ToggleType.DND, ToggleState.PermissionRequired) {}
        }
    }
}

@PreviewLightDark
@Composable
private fun ControlScreenPreview() = EztoggleTheme {
    ControlScreen(
        state = ControlUiState(
            states = mapOf(
                ToggleType.WIFI to ToggleState.On,
                ToggleType.MOBILE_DATA to ToggleState.Unknown,
                ToggleType.LOCATION to ToggleState.Off,
                ToggleType.AIRPLANE to ToggleState.Off,
                ToggleType.DND to ToggleState.PermissionRequired,
            ),
            dndAccess = false,
            phonePermission = false,
        ),
        onIntent = {},
        onOpenChecklist = {},
    )
}
