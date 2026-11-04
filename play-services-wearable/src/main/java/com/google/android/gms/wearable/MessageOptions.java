/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;

public class MessageOptions extends AutoSafeParcelable {
    @Field(2)
    public int priority;

    public static final Creator<MessageOptions> CREATOR = findCreator(MessageOptions.class);
}
