/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetNodeIdResponse extends AutoSafeParcelable {
    @Field(2)
    public int statusCode;
    @Field(3)
    public String nodeId;

    public static final Creator<GetNodeIdResponse> CREATOR = findCreator(GetNodeIdResponse.class);
}
