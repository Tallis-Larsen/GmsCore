/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class SaveRestoreStateRequest extends AutoSafeParcelable {
    @Field(1)
    public String nodeId;
    @Field(2)
    public int state;
    @Field(3)
    public byte[] data;

    public static final Creator<SaveRestoreStateRequest> CREATOR = findCreator(SaveRestoreStateRequest.class);
}
