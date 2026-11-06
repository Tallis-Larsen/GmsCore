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
public class GoogleAccount extends AbstractSafeParcelable {
    @Field(1)
    public String obfuscatedGaiaId;
    @Field(2)
    public String type;
    @Field(3)
    public String name;

    @Constructor
    public GoogleAccount() {
    }

    public static final SafeParcelableCreatorAndWriter<GoogleAccount> CREATOR = findCreator(GoogleAccount.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
