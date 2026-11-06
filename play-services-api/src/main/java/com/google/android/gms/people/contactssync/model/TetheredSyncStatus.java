/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.people.contactssync.model;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;

@SafeParcelable.Class
public class TetheredSyncStatus extends AbstractSafeParcelable {
    @Field(2)
    public int code;
    @Field(3)
    public String message;

    @Constructor
    public TetheredSyncStatus() {
    }

    public static final SafeParcelableCreatorAndWriter<TetheredSyncStatus> CREATOR = findCreator(TetheredSyncStatus.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
