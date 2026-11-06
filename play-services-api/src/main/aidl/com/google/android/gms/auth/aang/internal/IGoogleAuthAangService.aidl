/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.auth.aang.internal;

import com.google.android.gms.auth.aang.FetchAppRestrictionRequest;
import com.google.android.gms.auth.aang.GetAccountsRequest;
import com.google.android.gms.auth.aang.GetTokenRequest;
import com.google.android.gms.auth.aang.HasCapabilitiesRequest;
import com.google.android.gms.auth.aang.internal.IGoogleAuthAangCallbacks;
import com.google.android.gms.common.api.internal.IStatusCallback;

interface IGoogleAuthAangService {
    void getAccounts(IGoogleAuthAangCallbacks callbacks, in GetAccountsRequest request) = 0;
    void getToken(IGoogleAuthAangCallbacks callbacks, in GetTokenRequest request) = 1;
    void clearToken(IStatusCallback callback, String token) = 2;
    void hasCapabilities(IGoogleAuthAangCallbacks callbacks, in HasCapabilitiesRequest request) = 3;
    void fetchAppRestriction(IGoogleAuthAangCallbacks callbacks, in FetchAppRestrictionRequest request) = 4;
}
