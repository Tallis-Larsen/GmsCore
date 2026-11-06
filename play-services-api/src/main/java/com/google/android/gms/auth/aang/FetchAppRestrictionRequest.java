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
public class FetchAppRestrictionRequest extends AbstractSafeParcelable {
    @Field(1)
    public GoogleAccount account;
    @Field(2)
    public String packageName;

    @Constructor
    public FetchAppRestrictionRequest() {
    }

    public static final SafeParcelableCreatorAndWriter<FetchAppRestrictionRequest> CREATOR = findCreator(FetchAppRestrictionRequest.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
