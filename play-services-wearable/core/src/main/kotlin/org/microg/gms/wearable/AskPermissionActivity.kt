/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.core.content.ContextCompat

const val EXTRA_MESSENGER = "messenger"
const val EXTRA_PERMISSIONS = "permissions"

private const val TAG = "GmsWearPermission"
private const val REQUEST_CODE_PERMISSION = 120

class AskPermissionActivity : Activity() {
    private val permissions: Array<String>
        get() = intent?.getStringArrayExtra(EXTRA_PERMISSIONS) ?: emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            finishWithReply(RESULT_OK)
        } else {
            requestPermissions(permissions, REQUEST_CODE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            Log.d(TAG, "onRequestPermissionsResult: permissions:${permissions.joinToString(",")} grantResults:${grantResults.joinToString(",")}")
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            finishWithReply(if (granted) RESULT_OK else RESULT_CANCELED)
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun finishWithReply(code: Int) {
        intent?.getParcelableExtra<Messenger>(EXTRA_MESSENGER)?.let {
            runCatching { it.send(Message.obtain().apply { what = code }) }
        }
        finish()
    }
}
