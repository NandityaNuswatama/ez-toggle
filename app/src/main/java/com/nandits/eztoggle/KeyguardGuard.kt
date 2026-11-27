package com.nandits.eztoggle

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context

/** FR-3: no entry point may change a setting while the keyguard is up. */
object KeyguardGuard {

    /** Pure decision logic. [requestDismiss] reports whether the user unlocked. */
    fun guard(
        locked: Boolean,
        requestDismiss: (onResult: (Boolean) -> Unit) -> Unit,
        action: () -> Unit,
        abort: () -> Unit = {},
    ) {
        if (!locked) return action()
        requestDismiss { unlocked -> if (unlocked) action() else abort() }
    }

    fun isLocked(context: Context) = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    fun runUnlocked(activity: Activity, abort: () -> Unit = {}, action: () -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        guard(
            locked = keyguard.isKeyguardLocked,
            requestDismiss = { done ->
                keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = done(true)
                    override fun onDismissError() = done(false)
                    override fun onDismissCancelled() = done(false)
                })
            },
            action = action,
            abort = abort,
        )
    }

    /** For non-activity entry points (widget callbacks): no way to ask for unlock, so locked = abort. */
    fun runIfUnlocked(context: Context, action: () -> Unit) =
        guard(isLocked(context), requestDismiss = { it(false) }, action = action)
}
