/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetEapIdResponse extends AutoSafeParcelable {
    @Field(2)
    public int statusCode;
    @Field(3)
    public String eapId;

    public static final Creator<GetEapIdResponse> CREATOR = findCreator(GetEapIdResponse.class);
}
