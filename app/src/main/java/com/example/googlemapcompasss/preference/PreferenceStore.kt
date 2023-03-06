/*
 * This file is part of Compass.
 * Copyright (C) 2022 Philipp Bobek <philipp.bobek@mailbox.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Compass is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.googlemapcompasss.preference

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.MutableLiveData

private const val TAG = "PreferenceStore"
private const val SCREEN_ORIENTATION_LOCKED = "screen_orientation_locked"
private const val NIGHT_MODE = "night_mode"

class PreferenceStore(context: Context) {

    val screenOrientationLocked = MutableLiveData<Boolean>()
    val nightMode = MutableLiveData<Int>()

    private val sharedPreferenceChangeListener = SharedPreferenceChangeListener()
    private val screenOrientationLockedObserver = getScreenOrientationLockedObserver()
    private val nightModeObserver = getNightModeObserver()

    private val sharedPreferences: SharedPreferences

    init {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        updateScreenOrientationLocked()
        updateNightMode()

        screenOrientationLocked.observeForever(screenOrientationLockedObserver)
        nightMode.observeForever(nightModeObserver)

        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        screenOrientationLocked.removeObserver(screenOrientationLockedObserver)
        nightMode.removeObserver(nightModeObserver)
    }

    private inner class SharedPreferenceChangeListener : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            when (key) {
                SCREEN_ORIENTATION_LOCKED -> updateScreenOrientationLocked()
                NIGHT_MODE -> updateNightMode()
            }
        }
    }

    private fun updateScreenOrientationLocked() {
        val storedValue = sharedPreferences.getBoolean(SCREEN_ORIENTATION_LOCKED, false)
        if (screenOrientationLocked.value != storedValue) {
            screenOrientationLocked.value = storedValue
        }
    }

    private fun updateNightMode() {
        val storedValue = sharedPreferences.getInt(NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (nightMode.value != storedValue) {
            nightMode.value = storedValue
        }
    }

    private fun getScreenOrientationLockedObserver(): (t: Boolean) -> Unit = {
        val edit = sharedPreferences.edit()
        edit.putBoolean(SCREEN_ORIENTATION_LOCKED, it)
        edit.apply()
        Log.d(TAG, "Persisted screenOrientationLocked: $it")
    }

    private fun getNightModeObserver(): (t: Int) -> Unit = {
        val edit = sharedPreferences.edit()
        edit.putInt(NIGHT_MODE, it)
        edit.apply()
        Log.d(TAG, "Persisted nightMode: $it")
    }
}
