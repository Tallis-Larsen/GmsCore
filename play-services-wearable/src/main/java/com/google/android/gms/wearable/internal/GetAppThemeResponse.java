/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import com.google.android.gms.wearable.AppTheme;
import org.microg.safeparcel.AutoSafeParcelable;

public class GetAppThemeResponse extends AutoSafeParcelable {
    @Field(2)
    public int statusCode;
    @Field(3)
    public AppTheme appTheme;

    public static final Creator<GetAppThemeResponse> CREATOR = findCreator(GetAppThemeResponse.class);
}
