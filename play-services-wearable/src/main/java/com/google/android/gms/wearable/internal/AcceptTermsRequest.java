/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class AcceptTermsRequest extends AutoSafeParcelable {
    @Field(1)
    public int field1;
    @Field(2)
    public List<String> field2;
    @Field(3)
    public String field3;
    @Field(4)
    public String field4;
    @Field(5)
    public String field5;
    @Field(6)
    public String field6;
    @Field(7)
    public List<String> field7;
    @Field(8)
    public boolean field8;

    public static final Creator<AcceptTermsRequest> CREATOR = findCreator(AcceptTermsRequest.class);
}
