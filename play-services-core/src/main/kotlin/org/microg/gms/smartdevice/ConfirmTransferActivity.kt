/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import android.app.KeyguardManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.ResultReceiver
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.microg.gms.cryptauth.isLockscreenConfigured

class ConfirmTransferActivity : FragmentActivity() {
    private var resultReceiver: ResultReceiver? = null
    private var answered = false
    private var confirmingLockScreen = false

    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        reply(it.resultCode == RESULT_OK, lockScreenConfirmed = it.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        if (resultReceiver == null) {
            answered = true
            finish()
            return
        }
        confirmingLockScreen = savedInstanceState?.getBoolean(KEY_CONFIRMING_LOCK_SCREEN) == true
        if (confirmingLockScreen) return
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME).takeUnless { it.isNullOrEmpty() } ?: getString(R.string.smartdevice_unknown_device)
        val title = getString(R.string.smartdevice_confirm_transfer_title, deviceName)
        val accounts = intent.getStringArrayExtra(EXTRA_ACCOUNTS).orEmpty().joinToString("\n")
        val confirmIntent = if (SDK_INT >= 21 && isLockscreenConfigured()) {
            getSystemService<KeyguardManager>()?.createConfirmDeviceCredentialIntent(title, accounts)
        } else {
            null
        }
        if (confirmIntent != null) {
            confirmingLockScreen = true
            confirmCredential.launch(confirmIntent)
        } else {
            val view = layoutInflater.inflate(R.layout.smartdevice_confirm_transfer, null)
            view.findViewById<TextView>(R.id.confirm_transfer_title).text = title
            view.findViewById<TextView>(R.id.confirm_transfer_accounts).text = accounts
            view.findViewById<View>(R.id.confirm_transfer_cancel).setOnClickListener { reply(false) }
            view.findViewById<View>(R.id.confirm_transfer_confirm).setOnClickListener { reply(true) }
            BottomSheetDialog(this).apply {
                setContentView(view)
                setOnCancelListener { reply(false) }
            }.show()
        }
    }

    private fun reply(confirmed: Boolean, lockScreenConfirmed: Boolean = false) {
        if (answered) return
        answered = true
        resultReceiver?.send(if (confirmed) RESULT_OK else RESULT_CANCELED, bundleOf(EXTRA_LOCK_SCREEN_CONFIRMED to lockScreenConfirmed))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CONFIRMING_LOCK_SCREEN, confirmingLockScreen)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) reply(false)
    }

    companion object {
        private const val KEY_CONFIRMING_LOCK_SCREEN = "confirmingLockScreen"
        const val EXTRA_ACCOUNTS = "accounts"
        const val EXTRA_DEVICE_NAME = "deviceName"
        const val EXTRA_RESULT_RECEIVER = "resultReceiver"
        const val EXTRA_LOCK_SCREEN_CONFIRMED = "lockScreenConfirmed"
    }
}
