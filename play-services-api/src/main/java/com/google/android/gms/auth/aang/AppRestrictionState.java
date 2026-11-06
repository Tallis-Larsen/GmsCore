/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.auth.aang;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;

@SafeParcelable.Class
public class AppRestrictionState extends AbstractSafeParcelable {
    @Field(1)
    public boolean restricted;
    @Field(2)
    public boolean accountHidden;

    @Constructor
    public AppRestrictionState() {
    }

    public static final SafeParcelableCreatorAndWriter<AppRestrictionState> CREATOR = findCreator(AppRestrictionState.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
