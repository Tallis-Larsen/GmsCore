/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class GetLocalCapabilitiesResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(2)
    public List<String> field2;
    @Field(3)
    public List<String> field3;

    public static final Creator<GetLocalCapabilitiesResponse> CREATOR = findCreator(GetLocalCapabilitiesResponse.class);
}
