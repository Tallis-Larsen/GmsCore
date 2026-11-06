/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.R
import org.microg.gms.wearable.WearablePreferences
import org.microg.gms.wearable.getConnectedNodeNames

class WearableFragment : PreferenceFragmentCompat() {
    private lateinit var switchBarPreference: SwitchBarPreference
    private lateinit var permission: Preference
    private lateinit var callPermission: Preference
    private lateinit var status: Preference
    private var permissionRequestCode = 160

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_wearable)
    }

    @SuppressLint("RestrictedApi")
    override fun onBindPreferences() {
        switchBarPreference = preferenceScreen.findPreference(PREF_KEEP_CONNECTED) ?: switchBarPreference
        permission = preferenceScreen.findPreference(PREF_PERMISSION) ?: permission
        callPermission = preferenceScreen.findPreference(PREF_CALL_PERMISSION) ?: callPermission
        status = preferenceScreen.findPreference(PREF_STATUS) ?: status
        permission.setOnPreferenceClickListener {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), ++permissionRequestCode)
            true
        }
        callPermission.setOnPreferenceClickListener {
            requestPermissions(arrayOf(Manifest.permission.ANSWER_PHONE_CALLS), ++permissionRequestCode)
            true
        }
        switchBarPreference.setOnPreferenceChangeListener { _, newValue ->
            WearablePreferences.setKeepConnected(requireContext(), newValue as Boolean)
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            updateContent()
        }
    }

    override fun onResume() {
        super.onResume()
        updateContent()
    }

    private fun updateContent() {
        permission.isVisible = SDK_INT >= 31 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        callPermission.isVisible = SDK_INT >= 26 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED
        switchBarPreference.isChecked = WearablePreferences.isKeepConnected(requireContext())
        getConnectedNodeNames(requireContext()) { names ->
            if (!isAdded) return@getConnectedNodeNames
            status.summary = if (names.isEmpty()) {
                getString(R.string.wearable_state_disconnected)
            } else {
                getString(R.string.wearable_state_connected, names.joinToString())
            }
        }
    }

    companion object {
        const val PREF_KEEP_CONNECTED = "pref_wearable_keep_connected"
        const val PREF_PERMISSION = "pref_wearable_permission"
        const val PREF_CALL_PERMISSION = "pref_wearable_call_permission"
        const val PREF_STATUS = "pref_wearable_status"
    }
}
