/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.people.contactssync.internal;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;

@SafeParcelable.Class
public class DeviceContactsSyncSettingResponse extends AbstractSafeParcelable {
    @Field(1)
    public String accountName;
    @Field(4)
    public int state;

    @Constructor
    public DeviceContactsSyncSettingResponse() {
    }

    public static final SafeParcelableCreatorAndWriter<DeviceContactsSyncSettingResponse> CREATOR = findCreator(DeviceContactsSyncSettingResponse.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
