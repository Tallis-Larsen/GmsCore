/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class LogCounterRequest extends AutoSafeParcelable {
    @Field(1)
    public String field1;
    @Field(2)
    public long field2;
    @Field(3)
    public byte[] field3;
    @Field(4)
    public long field4;
    @Field(5)
    public boolean field5;

    public static final Creator<LogCounterRequest> CREATOR = findCreator(LogCounterRequest.class);
}
