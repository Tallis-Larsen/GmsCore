/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.smartdevice.directtransfer;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;
import com.google.android.gms.smartdevice.d2d.BootstrapAccount;

import org.microg.gms.utils.ToStringHelper;

import java.util.ArrayList;

@SafeParcelable.Class
public class DirectTransferConfigurations extends AbstractSafeParcelable {
    @Field(1)
    public String confirmationBodyText;
    @Field(2)
    public String thirdPartyConfirmationBodyText;
    @Field(3)
    public String confirmationTitleText;
    @Field(4)
    public String confirmationWatchIcon;
    @Field(5)
    public String theme;
    @Field(6)
    public boolean accountPickerEnabled;
    @Field(8)
    public String transitionTitle;
    @Field(9)
    public String transitionDescription;
    @Field(11)
    public ArrayList<BootstrapAccount> bootstrapAccounts;
    @Field(12)
    public int flowType;

    @Constructor
    public DirectTransferConfigurations() {
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringHelper.name("DirectTransferConfigurations")
                .field("accountPickerEnabled", accountPickerEnabled)
                .field("bootstrapAccounts", bootstrapAccounts)
                .field("flowType", flowType)
                .end();
    }

    public static final SafeParcelableCreatorAndWriter<DirectTransferConfigurations> CREATOR = findCreator(DirectTransferConfigurations.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
