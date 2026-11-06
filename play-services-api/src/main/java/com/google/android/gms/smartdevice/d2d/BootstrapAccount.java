/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.smartdevice.d2d;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;

import org.microg.gms.utils.ToStringHelper;

@SafeParcelable.Class
public class BootstrapAccount extends AbstractSafeParcelable {
    @Field(2)
    public String name;
    @Field(3)
    public String type;

    @Constructor
    public BootstrapAccount() {
    }

    public BootstrapAccount(String name, String type) {
        this.name = name;
        this.type = type;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringHelper.name("BootstrapAccount").field("name", name).field("type", type).end();
    }

    public static final SafeParcelableCreatorAndWriter<BootstrapAccount> CREATOR = findCreator(BootstrapAccount.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
