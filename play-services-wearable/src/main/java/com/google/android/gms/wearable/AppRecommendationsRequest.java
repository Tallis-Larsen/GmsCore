/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;

public class AppRecommendationsRequest extends AutoSafeParcelable {
    @Field(1)
    public String androidId;
    @Field(2)
    public int searchFlags;
    @Field(3)
    public int limit;

    public static final Creator<AppRecommendationsRequest> CREATOR = findCreator(AppRecommendationsRequest.class);
}
