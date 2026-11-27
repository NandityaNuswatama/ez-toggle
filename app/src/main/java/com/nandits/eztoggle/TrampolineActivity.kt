package com.nandits.eztoggle

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.lifecycleScope
import com.nandits.eztoggle.widget.refreshWidgets
import kotlinx.coroutines.launch

/**
 * Invisible hop between a widget tap and system UI: unlock check, launch the panel,
 * refresh the widget when the user comes back, finish.
 *
 * Not `noHistory` (despite PRD §9): noHistory finishes us the moment the panel opens,
 * so we would never see the result and could not refresh the widget.
 */
class TrampolineActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TYPE = "toggle_type"
    }

    private val panel = registerForActivityResult(StartActivityForResult()) { finishWithRefresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // recreated while the panel is open; result still arrives
        val type = ToggleType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) }
            ?: return finish()
        KeyguardGuard.runUnlocked(this, abort = ::finish) {
            val settings = AndroidDeviceSettings(this)
            val launch = settings.apply(ToggleActionHandler(settings).resolve(type))
            if (launch == null) return@runUnlocked finishWithRefresh()
            try {
                panel.launch(Intent(launch))
            } catch (_: ActivityNotFoundException) {
                finishWithRefresh()
            }
        }
    }

    private fun finishWithRefresh() {
        lifecycleScope.launch {
            refreshWidgets(applicationContext)
            finish()
        }
    }
}
