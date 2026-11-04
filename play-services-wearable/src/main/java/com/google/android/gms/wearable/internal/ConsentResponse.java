/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import java.util.List;
import org.microg.safeparcel.AutoSafeParcelable;

public class ConsentResponse extends AutoSafeParcelable {
    @Field(1)
    public int statusCode;
    @Field(2)
    public boolean hasTosConsent;
    @Field(3)
    public boolean hasLoggingConsent;
    @Field(4)
    public boolean hasCloudSyncConsent;
    @Field(5)
    public boolean hasLocationConsent;
    @Field(value = 6, subClass = AccountConsentRecordParcelable.class)
    public List<AccountConsentRecordParcelable> accountConsentRecords;
    @Field(7)
    public String nodeId;
    @Field(8)
    public Long lastUpdateRequestedTime;

    public static final Creator<ConsentResponse> CREATOR = findCreator(ConsentResponse.class);
}
