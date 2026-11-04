/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetCachedBackupsResponse extends AutoSafeParcelable {
    @Field(2)
    public int statusCode;
    @Field(3)
    public boolean field3;
    @Field(4)
    public ParcelableBackupDevice[] devices;

    public static final Creator<GetCachedBackupsResponse> CREATOR = findCreator(GetCachedBackupsResponse.class);
}
