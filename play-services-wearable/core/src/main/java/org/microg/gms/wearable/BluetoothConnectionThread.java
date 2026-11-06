/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.microg.wearable.SocketWearableConnection;
import org.microg.wearable.WearableConnection;

import java.io.IOException;
import java.util.UUID;

public class BluetoothConnectionThread extends Thread {
    private static final String TAG = "GmsWearBtThread";
    public static final UUID WEARABLE_BT_UUID = UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");
    private static final long MIN_RETRY_DELAY = 1000;
    private static final long MAX_RETRY_DELAY = 30000;

    private final BluetoothAdapter adapter;
    private final String address;
    private final WearableConnection.Listener listener;
    private final Object retryLock = new Object();
    private boolean retryNow;
    private volatile boolean closed;
    private volatile BluetoothSocket socket;

    public BluetoothConnectionThread(BluetoothAdapter adapter, String address, WearableConnection.Listener listener) {
        super("WearableBt-" + address);
        this.adapter = adapter;
        this.address = address;
        this.listener = listener;
    }

    @Override
    public void run() {
        long retryDelay = MIN_RETRY_DELAY;
        while (!closed) {
            try {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    socket = device.createRfcommSocketToServiceRecord(WEARABLE_BT_UUID);
                    socket.connect();
                    Log.d(TAG, "Connected to " + address);
                    retryDelay = MIN_RETRY_DELAY;
                    new SocketWearableConnection(socket.getInputStream(), socket.getOutputStream(), socket, listener).run();
                    Log.d(TAG, "Disconnected from " + address);
                } else {
                    Log.d(TAG, address + " is not bonded yet");
                }
            } catch (IOException e) {
                Log.d(TAG, "Connecting to " + address + " failed: " + e.getMessage());
            } catch (SecurityException e) {
                Log.w(TAG, "Missing Bluetooth permission", e);
            } finally {
                closeSocket();
            }
            if (closed) break;
            synchronized (retryLock) {
                if (!retryNow) {
                    try {
                        retryLock.wait(retryDelay);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (retryNow) {
                    retryNow = false;
                    retryDelay = MIN_RETRY_DELAY;
                } else {
                    retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
                }
            }
        }
    }

    public String getAddress() {
        return address;
    }

    public void retryNow() {
        synchronized (retryLock) {
            retryNow = true;
            retryLock.notify();
        }
    }

    public void close() {
        closed = true;
        closeSocket();
        interrupt();
    }

    private void closeSocket() {
        BluetoothSocket socket = this.socket;
        this.socket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
