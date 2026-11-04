/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class ConsentStatusRequest extends AutoSafeParcelable {
    @Field(1)
    public String field1;

    public static final Creator<ConsentStatusRequest> CREATOR = findCreator(ConsentStatusRequest.class);
}
