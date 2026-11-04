/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class WebIconParcelable extends AutoSafeParcelable {
    @Field(1)
    public String field1;
    @Field(2)
    public int field2;
    @Field(3)
    public int field3;

    public static final Creator<WebIconParcelable> CREATOR = findCreator(WebIconParcelable.class);
}
