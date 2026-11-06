/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.ConnectionConfiguration;

import org.microg.gms.common.ForegroundServiceContext;

public class TriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "GmsWearTrigger";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!WearablePreferences.isKeepConnected(context)) {
            context.stopService(new Intent(context, WearableService.class));
            return;
        }
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (hasBluetoothConfiguration(context, device != null ? device.getAddress() : null)) {
            Log.d(TAG, "Starting service, triggered by: " + intent);
            new ForegroundServiceContext(context).startService(new Intent(context, WearableService.class));
        }
    }

    private static boolean hasBluetoothConfiguration(Context context, String address) {
        ConfigurationDatabaseHelper configDatabase = new ConfigurationDatabaseHelper(context);
        try {
            ConnectionConfiguration[] configurations = configDatabase.getAllConfigurations();
            if (configurations == null) return false;
            for (ConnectionConfiguration config : configurations) {
                if (WearableImpl.isBluetoothConfiguration(config) && (address == null || address.equals(config.address))) {
                    return true;
                }
            }
            return false;
        } finally {
            configDatabase.close();
        }
    }
}
