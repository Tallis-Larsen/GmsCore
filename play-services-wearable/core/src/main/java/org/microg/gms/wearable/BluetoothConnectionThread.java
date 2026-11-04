/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothDevice;

import org.microg.wearable.WearableConnection;

import java.util.UUID;

/**
 * Connects to a watch's "WearableBt" RFCOMM service as a client, the way the phone does
 * for a {@code ConnectionConfiguration} of type 5, and runs the usual framing over it.
 */
public class BluetoothConnectionThread extends Thread {
    public static final UUID WEARABLE_BT_UUID = UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");

    private final BluetoothDevice device;
    private final WearableConnection.Listener listener;

    public BluetoothConnectionThread(BluetoothDevice device, WearableConnection.Listener listener) {
        this.device = device;
        this.listener = listener;
    }

    @Override
    public void run() {
    }

    public void close() {
    }
}
