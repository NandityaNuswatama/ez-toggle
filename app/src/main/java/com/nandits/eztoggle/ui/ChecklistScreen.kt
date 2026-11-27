package com.nandits.eztoggle.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.nandits.eztoggle.R
import com.nandits.eztoggle.ui.theme.EztoggleTheme
import com.nandits.eztoggle.widget.SafeToggleWidgetReceiver
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.checklistStore by preferencesDataStore("checklist")

private class ChecklistItem(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:StringRes val actionLabel: Int? = null,
    val action: (Context) -> Unit = {},
)

private fun Context.open(action: String) = startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

// Order is the persisted key ("item_<index>"): append only.
private val items = listOf(
    ChecklistItem(R.string.check_qs_title, R.string.check_qs_body),
    ChecklistItem(R.string.check_widget_title, R.string.check_widget_body, R.string.check_widget_action) { ctx ->
        val manager = ctx.getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(ctx, SafeToggleWidgetReceiver::class.java), null, null)
        }
    },
    ChecklistItem(R.string.check_dnd_title, R.string.check_dnd_body, R.string.open_settings) {
        it.open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    },
    ChecklistItem(R.string.check_theft_title, R.string.check_theft_body, R.string.open_settings) {
        it.open(Settings.ACTION_SECURITY_SETTINGS)
    },
    ChecklistItem(R.string.check_assistant_title, R.string.check_assistant_body, R.string.open_settings) {
        it.open(Settings.ACTION_VOICE_INPUT_SETTINGS)
    },
    ChecklistItem(R.string.check_update_title, R.string.check_update_body),
)

private fun key(index: Int) = booleanPreferencesKey("item_$index")

@Composable
fun ChecklistRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checked by remember {
        context.checklistStore.data.map { prefs -> items.indices.filter { prefs[key(it)] == true }.toSet() }
    }.collectAsState(emptySet())
    ChecklistScreen(
        checked = checked,
        onCheck = { index, value -> scope.launch { context.checklistStore.edit { it[key(index)] = value } } },
        onAction = { index -> items[index].action(context) },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    checked: Set<Int>,
    onCheck: (Int, Boolean) -> Unit,
    onAction: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checklist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
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
            itemsIndexed(items) { index, item ->
                OutlinedCard {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(8.dp)) {
                        Checkbox(checked = index in checked, onCheckedChange = { onCheck(index, it) })
                        Column(Modifier.weight(1f).padding(top = 12.dp, end = 8.dp)) {
                            Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium)
                            item.actionLabel?.let {
                                TextButton(onClick = { onAction(index) }) { Text(stringResource(it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChecklistPreview() = EztoggleTheme {
    ChecklistScreen(checked = setOf(0, 2), onCheck = { _, _ -> }, onAction = {}, onBack = {})
}
