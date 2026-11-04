/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;

public class ConnectionDelayConfig extends AutoSafeParcelable {
    @Field(1)
    public long field1;
    @Field(2)
    public long field2;

    public static final Creator<ConnectionDelayConfig> CREATOR = findCreator(ConnectionDelayConfig.class);
}
