/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class AppRecommendationsResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(value = 2, subClass = AppParcelable.class)
    public List<AppParcelable> apps;
    @Field(3)
    public WebIconParcelable icon;

    public static final Creator<AppRecommendationsResponse> CREATOR = findCreator(AppRecommendationsResponse.class);
}
