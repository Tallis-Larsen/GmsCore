/*
 * Copyright (C) 2019 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.wearable;

import android.content.Context;
import android.net.Uri;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.google.android.gms.wearable.internal.CapabilityInfoParcelable;
import com.google.android.gms.wearable.internal.NodeParcelable;

import org.microg.gms.common.PackageUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CapabilityManager {
    public static final String PATH_PREFIX = "/capabilities/";
    public static final byte[] DATA_STATIC = {'s'};
    private static final byte[] DATA_DYNAMIC = {'d'};
    private static final int FILTER_REACHABLE = 1;

    private final WearableImpl wearable;
    private final String prefix;

    public CapabilityManager(Context context, WearableImpl wearable, String packageName) {
        this.wearable = wearable;
        this.prefix = PATH_PREFIX + packageName + "/" + PackageUtils.firstSignatureDigest(context, packageName) + "/";
    }

    public static String getPath(String packageName, String signatureDigest, String capability) {
        return PATH_PREFIX + packageName + "/" + signatureDigest + "/" + Uri.encode(capability);
    }

    private String getPath(String capability) {
        return prefix + Uri.encode(capability);
    }

    private boolean isLocallySet(String capability) {
        DataItemRecord record = wearable.getGmsDataItem(getPath(capability));
        return record != null && !record.deleted;
    }

    public CapabilityInfoParcelable getCapability(String capability, int filter) {
        CapabilityInfoParcelable info = getCapabilities(getPath(capability), filter).get(capability);
        return info != null ? info : new CapabilityInfoParcelable(capability, new ArrayList<>());
    }

    public List<CapabilityInfoParcelable> getAllCapabilities(int filter) {
        return new ArrayList<>(getCapabilities(prefix, filter).values());
    }

    public List<String> getLocalCapabilities() {
        List<String> capabilities = new ArrayList<>();
        DataHolder dataHolder = wearable.getGmsDataItems(prefix);
        for (int i = 0; i < dataHolder.getCount(); i++) {
            String path = dataHolder.getString("path", i, 0);
            if (wearable.getLocalNodeId().equals(dataHolder.getString("host", i, 0)) && isDirectChild(path)) {
                capabilities.add(Uri.decode(path.substring(prefix.length())));
            }
        }
        dataHolder.close();
        return capabilities;
    }

    private Map<String, CapabilityInfoParcelable> getCapabilities(String path, int filter) {
        Map<String, List<NodeParcelable>> nodes = new HashMap<>();
        DataHolder dataHolder = wearable.getGmsDataItems(path);
        for (int i = 0; i < dataHolder.getCount(); i++) {
            String itemPath = dataHolder.getString("path", i, 0);
            String host = dataHolder.getString("host", i, 0);
            if (!isDirectChild(itemPath) || wearable.getLocalNodeId().equals(host)) continue;
            if (filter == FILTER_REACHABLE && !wearable.isConnected(host)) continue;
            String capability = Uri.decode(itemPath.substring(prefix.length()));
            if (!nodes.containsKey(capability)) nodes.put(capability, new ArrayList<>());
            nodes.get(capability).add(wearable.getNodeParcelable(host));
        }
        dataHolder.close();
        Map<String, CapabilityInfoParcelable> capabilities = new HashMap<>();
        for (Map.Entry<String, List<NodeParcelable>> entry : nodes.entrySet()) {
            capabilities.put(entry.getKey(), new CapabilityInfoParcelable(entry.getKey(), entry.getValue()));
        }
        return capabilities;
    }

    private boolean isDirectChild(String path) {
        return path.startsWith(prefix) && path.indexOf('/', prefix.length()) == -1;
    }

    public int add(String capability) {
        if (isLocallySet(capability)) {
            return WearableStatusCodes.DUPLICATE_CAPABILITY;
        }
        wearable.putGmsDataItem(getPath(capability), DATA_DYNAMIC);
        return CommonStatusCodes.SUCCESS;
    }

    public int remove(String capability) {
        if (!isLocallySet(capability)) {
            return WearableStatusCodes.UNKNOWN_CAPABILITY;
        }
        wearable.deleteGmsDataItem(getPath(capability));
        return CommonStatusCodes.SUCCESS;
    }
}
