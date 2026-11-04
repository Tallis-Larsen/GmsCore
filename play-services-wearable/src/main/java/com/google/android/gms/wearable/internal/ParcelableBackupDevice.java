/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class ParcelableBackupDevice extends AutoSafeParcelable {
    @Field(1)
    public byte[] data;

    public static final Creator<ParcelableBackupDevice> CREATOR = findCreator(ParcelableBackupDevice.class);
}
