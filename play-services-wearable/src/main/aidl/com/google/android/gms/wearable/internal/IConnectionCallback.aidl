/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import com.google.android.gms.wearable.internal.ConnectionEvent;

interface IConnectionCallback {
    void onConnectionEvent(in ConnectionEvent event) = 1;
}
