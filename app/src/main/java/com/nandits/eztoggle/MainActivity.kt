package com.nandits.eztoggle

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nandits.eztoggle.ui.ChecklistRoute
import com.nandits.eztoggle.ui.ControlScreen
import com.nandits.eztoggle.ui.theme.EztoggleTheme
import com.nandits.eztoggle.widget.refreshWidgets
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ControlViewModel by viewModels {
        viewModelFactory { initializer { ControlViewModel(AndroidDeviceSettings(applicationContext)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EztoggleTheme {
                val state by viewModel.state.collectAsState()
                var checklist by rememberSaveable { mutableStateOf(false) }
                val phonePermission = rememberLauncherForActivityResult(RequestPermission()) {
                    viewModel.onIntent(ControlIntent.Refresh)
                }
                LaunchedEffect(Unit) {
                    for (effect in viewModel.effects) when (effect) {
                        is ControlEffect.Launch -> try {
                            startActivity(Intent(effect.intentAction))
                        } catch (_: ActivityNotFoundException) {
                        }
                        ControlEffect.RequestPhonePermission -> phonePermission.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }
                BackHandler(enabled = checklist) { checklist = false }
                if (checklist) {
                    ChecklistRoute(onBack = { checklist = false })
                } else {
                    ControlScreen(state, onIntent = ::dispatch, onOpenChecklist = { checklist = true })
                }
            }
        }
    }

    private fun dispatch(intent: ControlIntent) {
        if (intent !is ControlIntent.Tap) return viewModel.onIntent(intent)
        KeyguardGuard.runUnlocked(this) {
            viewModel.onIntent(intent)
            lifecycleScope.launch { refreshWidgets(applicationContext) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onIntent(ControlIntent.Refresh)
        lifecycleScope.launch { refreshWidgets(applicationContext) }
    }
}
