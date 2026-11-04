/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class AppWearDetailsParcelable extends AutoSafeParcelable {
    @Field(1)
    public boolean isWatchface;
    @Field(2)
    public List<String> watchfaceCategories;

    public static final Creator<AppWearDetailsParcelable> CREATOR = findCreator(AppWearDetailsParcelable.class);
}
