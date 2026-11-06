/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

private const val EXTRA_CONNECTED_NODE_NAMES = "org.microg.gms.wearable.CONNECTED_NODE_NAMES"

class ServiceInfoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        setResultExtras(Bundle().apply {
            putStringArrayList(EXTRA_CONNECTED_NODE_NAMES, ArrayList(WearableService.getConnectedNodeNames()))
        })
    }
}

fun getConnectedNodeNames(context: Context, callback: (List<String>) -> Unit) {
    context.sendOrderedBroadcast(Intent(context, ServiceInfoReceiver::class.java), null, object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback(getResultExtras(true).getStringArrayList(EXTRA_CONNECTED_NODE_NAMES).orEmpty())
        }
    }, null, Activity.RESULT_OK, null, null)
}
