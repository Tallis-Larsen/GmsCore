/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class ConnectionRestrictions extends AutoSafeParcelable {
    @Field(value = 1, subClass = DataItemFilter.class)
    public List<DataItemFilter> allowedDataItemFilters;
    @Field(2)
    public List<String> allowedCapabilities;
    @Field(3)
    public List<String> allowedPackages;

    public static final Creator<ConnectionRestrictions> CREATOR = findCreator(ConnectionRestrictions.class);
}
