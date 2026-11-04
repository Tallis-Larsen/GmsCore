/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetFastpairAccountKeyByAccountResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(2)
    public FastPairAccountKeyParcelable accountKey;

    public static final Creator<GetFastpairAccountKeyByAccountResponse> CREATOR = findCreator(GetFastpairAccountKeyByAccountResponse.class);
}
