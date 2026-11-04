/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;

public class GetListenerServiceRequest extends AutoSafeParcelable {
    public static final Creator<GetListenerServiceRequest> CREATOR = findCreator(GetListenerServiceRequest.class);
}
