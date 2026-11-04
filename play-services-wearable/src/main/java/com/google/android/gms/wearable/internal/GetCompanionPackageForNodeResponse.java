/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetCompanionPackageForNodeResponse extends AutoSafeParcelable {
    @Field(2)
    public int statusCode;
    @Field(3)
    public String packageName;

    public static final Creator<GetCompanionPackageForNodeResponse> CREATOR = findCreator(GetCompanionPackageForNodeResponse.class);
}
