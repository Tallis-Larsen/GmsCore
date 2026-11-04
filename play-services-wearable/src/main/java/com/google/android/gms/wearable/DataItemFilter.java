/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import android.net.Uri;
import org.microg.safeparcel.AutoSafeParcelable;

public class DataItemFilter extends AutoSafeParcelable {
    @Field(1)
    public Uri uri;
    @Field(2)
    public int filterType;

    public static final Creator<DataItemFilter> CREATOR = findCreator(DataItemFilter.class);
}
