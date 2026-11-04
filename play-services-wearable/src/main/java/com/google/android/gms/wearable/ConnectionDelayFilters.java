/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class ConnectionDelayFilters extends AutoSafeParcelable {
    @Field(value = 1, subClass = DataItemFilter.class)
    public List<DataItemFilter> dataItemFilters;

    public static final Creator<ConnectionDelayFilters> CREATOR = findCreator(ConnectionDelayFilters.class);
}
