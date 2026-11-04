/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class LogEventRequest extends AutoSafeParcelable {
    @Field(1)
    public byte[] field1;

    public static final Creator<LogEventRequest> CREATOR = findCreator(LogEventRequest.class);
}
