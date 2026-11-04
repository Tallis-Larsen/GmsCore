/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class ConnectionStateEventParcelable extends AutoSafeParcelable {
    @Field(1)
    public int state;
    @Field(2)
    public String field2;

    public static final Creator<ConnectionStateEventParcelable> CREATOR = findCreator(ConnectionStateEventParcelable.class);
}
