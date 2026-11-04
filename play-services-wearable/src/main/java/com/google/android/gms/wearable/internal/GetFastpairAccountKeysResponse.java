/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class GetFastpairAccountKeysResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(value = 2, subClass = FastPairAccountKeyParcelable.class)
    public List<FastPairAccountKeyParcelable> accountKeys;

    public static final Creator<GetFastpairAccountKeysResponse> CREATOR = findCreator(GetFastpairAccountKeysResponse.class);
}
