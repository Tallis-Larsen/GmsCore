/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.smartdevice.directtransfer.internal;

import com.google.android.gms.common.api.Status;

interface IDirectTransferCallback {
    oneway void onResult(in Status status) = 0;
}
