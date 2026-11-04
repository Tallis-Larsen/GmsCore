/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class BooleanResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(2)
    public boolean value;

    public static final Creator<BooleanResponse> CREATOR = findCreator(BooleanResponse.class);
}
