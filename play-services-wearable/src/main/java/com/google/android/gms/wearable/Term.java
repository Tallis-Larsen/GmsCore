/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;

public class Term extends AutoSafeParcelable {
    @Field(1)
    public int termType;
    @Field(2)
    public String text;
    @Field(3)
    public boolean isExplicitConsent;
    @Field(4)
    public String title;
    @Field(5)
    public String field5;
    @Field(6)
    public int optInType;

    public static final Creator<Term> CREATOR = findCreator(Term.class);
}
