/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class FetchBackupRequest extends AutoSafeParcelable {
    @Field(1)
    public String field1;
    @Field(2)
    public String field2;

    public static final Creator<FetchBackupRequest> CREATOR = findCreator(FetchBackupRequest.class);
}
