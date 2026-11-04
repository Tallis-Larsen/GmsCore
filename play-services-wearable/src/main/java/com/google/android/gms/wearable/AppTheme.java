/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;

public class AppTheme extends AutoSafeParcelable {
    @Field(1)
    public int field1;
    @Field(2)
    public int field2;
    @Field(3)
    public int field3;
    @Field(4)
    public int field4;
    @Field(5)
    public int field5;
    @Field(6)
    public Icon icon;
    @Field(7)
    public Headline headline;
    @Field(8)
    public Description description;
    @Field(9)
    public CallToAction callToAction;

    public static class Icon extends AutoSafeParcelable {
        @Field(1)
        public int field1;

        public static final Creator<Icon> CREATOR = findCreator(Icon.class);
    }

    public static class Headline extends AutoSafeParcelable {
        @Field(1)
        public int field1;
        @Field(2)
        public int field2;
        @Field(3)
        public int field3;

        public static final Creator<Headline> CREATOR = findCreator(Headline.class);
    }

    public static class Description extends AutoSafeParcelable {
        @Field(1)
        public int field1;
        @Field(2)
        public int field2;

        public static final Creator<Description> CREATOR = findCreator(Description.class);
    }

    public static class CallToAction extends AutoSafeParcelable {
        @Field(1)
        public int field1;
        @Field(2)
        public int field2;
        @Field(3)
        public int field3;

        public static final Creator<CallToAction> CREATOR = findCreator(CallToAction.class);
    }

    public static final Creator<AppTheme> CREATOR = findCreator(AppTheme.class);
}
