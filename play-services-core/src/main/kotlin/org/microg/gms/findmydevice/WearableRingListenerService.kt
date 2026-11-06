/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.findmydevice

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

private const val PATH_RING = "com.google.android.gms.mdm.ACTION_RING"
private const val PATH_CANCEL_RING = "com.google.android.gms.mdm.ACTION_CANCEL_RING"

class WearableRingListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_RING -> AlarmRingService.startRing(this, messageEvent.sourceNodeId)
            PATH_CANCEL_RING -> AlarmRingService.stopRing(this)
        }
    }
}
