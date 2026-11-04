/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import com.google.android.gms.common.data.DataHolder;
import org.microg.safeparcel.AutoSafeParcelable;

public class NodeMigratedEventParcelable extends AutoSafeParcelable {
    @Field(1)
    public String field1;
    @Field(2)
    public DataHolder field2;

    public static final Creator<NodeMigratedEventParcelable> CREATOR = findCreator(NodeMigratedEventParcelable.class);
}
