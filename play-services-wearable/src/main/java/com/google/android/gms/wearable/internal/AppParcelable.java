/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class AppParcelable extends AutoSafeParcelable {
    @Field(1)
    public String field1;
    @Field(2)
    public String field2;
    @Field(3)
    public WebIconParcelable icon;
    @Field(4)
    public String field4;
    @Field(5)
    public String field5;
    @Field(6)
    public Float starRating;
    @Field(7)
    public AppWearDetailsParcelable wearDetails;

    public static final Creator<AppParcelable> CREATOR = findCreator(AppParcelable.class);
}
