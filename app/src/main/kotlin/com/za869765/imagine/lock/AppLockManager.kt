package com.za869765.imagine.lock

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.za869765.imagine.data.prefs.SecurePrefs

// Singleton lock state observable from Compose. Application registers it
// with ProcessLifecycleOwner to flip the flag whenever the app goes background.
class AppLockManager(private val prefs: SecurePrefs) : DefaultLifecycleObserver {

    private val _locked = mutableStateOf(true) // start locked on cold launch
    val lockedState: MutableState<Boolean> get() = _locked

    val isLocked: Boolean get() = _locked.value

    fun unlock() { _locked.value = false }
    fun lock() { _locked.value = true }

    override fun onStop(owner: LifecycleOwner) {
        if (prefs.lockOnBackground) {
            _locked.value = true
        }
    }

    companion object {
        @Volatile private var instance: AppLockManager? = null
        fun get(prefs: SecurePrefs): AppLockManager = instance ?: synchronized(this) {
            instance ?: AppLockManager(prefs).also {
                instance = it
                ProcessLifecycleOwner.get().lifecycle.addObserver(it)
            }
        }
    }
}
