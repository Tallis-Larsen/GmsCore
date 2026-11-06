/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.smartdevice.directtransfer.internal;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.common.api.ApiMetadata;
import com.google.android.gms.smartdevice.d2d.internal.IDirectTransferListener;
import com.google.android.gms.smartdevice.directtransfer.DirectTransferConfigurations;
import com.google.android.gms.smartdevice.directtransfer.internal.IDirectTransferCallback;

interface ISourceDirectTransferService {
    oneway void startDirectTransfer(IDirectTransferCallback callback, in DirectTransferConfigurations configurations, in ParcelFileDescriptor[] fileDescriptors, IDirectTransferListener listener, in ApiMetadata apiMetadata) = 0;
    oneway void abortDirectTransfer(IDirectTransferCallback callback, in ApiMetadata apiMetadata) = 1;
    oneway void isTransferInProgress(IDirectTransferCallback callback, in Bundle bundle, in ApiMetadata apiMetadata) = 2;
}
