/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.smartdevice.d2d.internal;

import android.app.PendingIntent;
import com.google.android.gms.smartdevice.d2d.AccountTransferResult;

interface IDirectTransferListener {
    void onAccountTransferResults(in AccountTransferResult[] results) = 0;
    void onShowUi(in PendingIntent pendingIntent) = 1;
    void onError(int statusCode, String message) = 2;
}
