/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import com.google.android.gms.wearable.Term;
import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class GetTermsResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(value = 2, subClass = Term.class)
    public List<Term> terms;

    public static final Creator<GetTermsResponse> CREATOR = findCreator(GetTermsResponse.class);
}
