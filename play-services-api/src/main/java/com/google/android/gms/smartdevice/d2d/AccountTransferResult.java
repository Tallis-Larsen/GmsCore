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
public class AccountTransferResult extends AbstractSafeParcelable {
    @Field(2)
    public BootstrapAccount bootstrapAccount;
    @Field(3)
    public int result;
    @Field(4)
    public int lockScreenAuthenticationType;

    @Constructor
    public AccountTransferResult() {
    }

    public AccountTransferResult(BootstrapAccount bootstrapAccount, int result, int lockScreenAuthenticationType) {
        this.bootstrapAccount = bootstrapAccount;
        this.result = result;
        this.lockScreenAuthenticationType = lockScreenAuthenticationType;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringHelper.name("AccountTransferResult")
                .field("bootstrapAccount", bootstrapAccount)
                .field("result", result)
                .field("lockScreenAuthenticationType", lockScreenAuthenticationType)
                .end();
    }

    public static final SafeParcelableCreatorAndWriter<AccountTransferResult> CREATOR = findCreator(AccountTransferResult.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
