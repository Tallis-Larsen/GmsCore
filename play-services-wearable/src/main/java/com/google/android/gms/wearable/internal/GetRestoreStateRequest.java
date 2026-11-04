/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetRestoreStateRequest extends AutoSafeParcelable {
    @Field(1)
    public String nodeId;

    public static final Creator<GetRestoreStateRequest> CREATOR = findCreator(GetRestoreStateRequest.class);
}
