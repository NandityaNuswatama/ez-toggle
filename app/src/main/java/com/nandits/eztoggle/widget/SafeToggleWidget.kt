package com.nandits.eztoggle.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nandits.eztoggle.AndroidDeviceSettings
import com.nandits.eztoggle.KeyguardGuard
import com.nandits.eztoggle.SettingsStateReader
import com.nandits.eztoggle.ToggleAction
import com.nandits.eztoggle.ToggleActionHandler
import com.nandits.eztoggle.ToggleState
import com.nandits.eztoggle.ToggleType
import com.nandits.eztoggle.TrampolineActivity
import com.nandits.eztoggle.apply

/** Bumped to force a recomposition of a live Glance session; the value itself is unused. */
private val RefreshKey = longPreferencesKey("refresh")

suspend fun refreshWidgets(context: Context) {
    val widget = SafeToggleWidget()
    GlanceAppWidgetManager(context).getGlanceIds(SafeToggleWidget::class.java).forEach { id ->
        updateAppWidgetState(context, id) { it[RefreshKey] = System.nanoTime() }
        widget.update(context, id)
    }
}

class SafeToggleWidget : GlanceAppWidget() {
    companion object {
        private val NARROW = DpSize(110.dp, 110.dp) // 2 rows, icons
        private val COMPACT = DpSize(240.dp, 48.dp) // 1 row, icons
        private val LABELED = DpSize(240.dp, 96.dp) // 1 row, icons + labels
    }

    override val sizeMode = SizeMode.Responsive(setOf(NARROW, COMPACT, LABELED))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reader = SettingsStateReader(AndroidDeviceSettings(context))
        provideContent {
            currentState(RefreshKey) // subscribe so refreshWidgets() recomposes and re-reads below
            GlanceTheme { WidgetContent(reader.all()) }
        }
    }
}

class SafeToggleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToggleWidget()
}

class DndToggleCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settings = AndroidDeviceSettings(context)
        KeyguardGuard.runIfUnlocked(context) {
            val action = ToggleActionHandler(settings).resolve(ToggleType.DND)
            if (action is ToggleAction.SetDnd) settings.apply(action)
        }
        refreshWidgets(context)
    }
}

@Composable
private fun WidgetContent(states: Map<ToggleType, ToggleState>) {
    val size = LocalSize.current
    val twoRows = size.width < 200.dp
    val labels = !twoRows && size.height >= 96.dp
    val types = ToggleType.entries
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(6.dp),
    ) {
        val rows = if (twoRows) listOf(types.take(3), types.drop(3)) else listOf(types)
        rows.forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEach { type ->
                    ToggleButton(type, states[type] ?: ToggleState.Unknown, labels, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(type: ToggleType, state: ToggleState, showLabel: Boolean, modifier: GlanceModifier) {
    val context = LocalContext.current
    val on = state == ToggleState.On
    val bg = if (on) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (on) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    val label = context.getString(type.label)
    val description = "$label, ${context.getString(state.label)}"
    val click = if (type == ToggleType.DND && state != ToggleState.PermissionRequired) {
        actionRunCallback<DndToggleCallback>()
    } else {
        actionStartActivity<TrampolineActivity>(
            actionParametersOf(ActionParameters.Key<String>(TrampolineActivity.EXTRA_TYPE) to type.name),
        )
    }
    Box(modifier = modifier.fillMaxHeight().padding(3.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg)
                .cornerRadius(android.R.dimen.system_app_widget_inner_radius)
                .clickable(click)
                .semantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(type.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(fg),
                // Off/Unknown are muted by alpha as well as color, so state is not color-only.
                alpha = if (on) 1f else 0.6f,
            )
        }
    }
}

private val previewStates = mapOf(
    ToggleType.WIFI to ToggleState.On,
    ToggleType.MOBILE_DATA to ToggleState.Unknown,
    ToggleType.LOCATION to ToggleState.Off,
    ToggleType.AIRPLANE to ToggleState.Off,
    ToggleType.DND to ToggleState.PermissionRequired,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 60)
@Composable
private fun CompactPreview() = GlanceTheme { WidgetContent(previewStates) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 110)
@Composable
private fun LabeledPreview() = GlanceTheme { WidgetContent(previewStates) }

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 130, heightDp = 130)
@Composable
private fun NarrowPreview() = GlanceTheme { WidgetContent(previewStates) }
