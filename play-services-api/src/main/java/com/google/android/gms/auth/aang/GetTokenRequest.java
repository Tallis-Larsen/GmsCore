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

import java.util.ArrayList;

@SafeParcelable.Class
public class GetTokenRequest extends AbstractSafeParcelable {
    @Field(1)
    public GoogleAccount account;
    @Field(3)
    public ArrayList<String> oauth2Scopes;
    @Field(4)
    public ArrayList<String> webLoginScopes;
    @Field(5)
    public ArrayList<String> services;
    @Field(6)
    public ArrayList<String> audiences;

    @Constructor
    public GetTokenRequest() {
    }

    public static final SafeParcelableCreatorAndWriter<GetTokenRequest> CREATOR = findCreator(GetTokenRequest.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
