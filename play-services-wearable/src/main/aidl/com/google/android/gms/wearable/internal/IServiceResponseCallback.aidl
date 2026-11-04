/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import com.google.android.gms.wearable.internal.IWearableListener;
import com.google.android.gms.wearable.internal.WearableListenerServiceInfo;

interface IServiceResponseCallback {
    void onServiceResponse(in WearableListenerServiceInfo info, IWearableListener listener) = 0;
}
