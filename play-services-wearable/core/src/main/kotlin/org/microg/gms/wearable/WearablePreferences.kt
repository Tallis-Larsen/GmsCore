/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable

import android.content.Context
import android.content.Intent
import org.microg.gms.settings.SettingsContract
import org.microg.gms.settings.SettingsContract.Wearable

object WearablePreferences {

    @JvmStatic
    fun isKeepConnected(context: Context): Boolean {
        val projection = arrayOf(Wearable.KEEP_CONNECTED)
        return SettingsContract.getSettings(context, Wearable.getContentUri(context), projection) { c ->
            c.getInt(0) != 0
        }
    }

    @JvmStatic
    fun setKeepConnected(context: Context, enabled: Boolean) {
        SettingsContract.setSettings(context, Wearable.getContentUri(context)) {
            put(Wearable.KEEP_CONNECTED, enabled)
        }
        context.sendBroadcast(Intent(context, TriggerReceiver::class.java))
    }

}
