/*
 * Copyright (C) 2013-2017 microG Project Team
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer;
import com.google.android.gms.wearable.ConnectionConfiguration;
import com.google.android.gms.wearable.ConnectionDelayFilters;
import com.google.android.gms.wearable.ConnectionRestrictions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ConfigurationDatabaseHelper extends SQLiteOpenHelper {

    public static final String NULL_STRING = "NULL_STRING";
    public static final String TABLE_NAME = "connectionConfigurations";
    public static final String BY_NAME = "name=?";

    public ConfigurationDatabaseHelper(Context context) {
        super(context, "connectionconfig.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE connectionConfigurations (_id INTEGER PRIMARY KEY AUTOINCREMENT,androidId TEXT,name TEXT NOT NULL,pairedBtAddress TEXT NOT NULL,connectionType INTEGER NOT NULL,role INTEGER NOT NULL,connectionEnabled INTEGER NOT NULL,nodeId TEXT, UNIQUE(name) ON CONFLICT REPLACE);");
        onUpgrade(db, 2, 3);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN packageName TEXT;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN allowedConfigPackages TEXT;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN isMigrating INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN dataItemSyncEnabled INTEGER DEFAULT 1;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN restrictions BLOB;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN removeConnectionWhenBondRemovedByUser INTEGER DEFAULT 1;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN connectionDelayFilters BLOB;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN maxSupportedRemoteAndroidSdkVersion INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN runtimeType INTEGER DEFAULT 0;");
            db.execSQL("ALTER TABLE connectionConfigurations ADD COLUMN skipConnectingIfNotBonded INTEGER DEFAULT 0;");
        }
    }

    private static ConnectionConfiguration configFromCursor(final Cursor cursor) {
        String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        String pairedBtAddress = cursor.getString(cursor.getColumnIndexOrThrow("pairedBtAddress"));
        int connectionType = cursor.getInt(cursor.getColumnIndexOrThrow("connectionType"));
        int role = cursor.getInt(cursor.getColumnIndexOrThrow("role"));
        int enabled = cursor.getInt(cursor.getColumnIndexOrThrow("connectionEnabled"));
        String nodeId = cursor.getString(cursor.getColumnIndexOrThrow("nodeId"));
        if (NULL_STRING.equals(name)) name = null;
        if (NULL_STRING.equals(pairedBtAddress)) pairedBtAddress = null;
        ConnectionConfiguration config = new ConnectionConfiguration(name, pairedBtAddress, connectionType, role, enabled > 0, nodeId);
        config.packageName = cursor.getString(cursor.getColumnIndexOrThrow("packageName"));
        String allowedConfigPackages = cursor.getString(cursor.getColumnIndexOrThrow("allowedConfigPackages"));
        if (allowedConfigPackages != null) {
            config.allowedConfigPackages = allowedConfigPackages.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(allowedConfigPackages.split(",")));
        }
        config.migrating = cursor.getInt(cursor.getColumnIndexOrThrow("isMigrating")) > 0;
        config.dataItemSyncEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("dataItemSyncEnabled")) > 0;
        byte[] restrictions = cursor.getBlob(cursor.getColumnIndexOrThrow("restrictions"));
        if (restrictions != null) {
            config.connectionRestrictions = SafeParcelableSerializer.deserializeFromBytes(restrictions, ConnectionRestrictions.CREATOR);
        }
        config.removeConnectionWhenBondRemovedByUser = cursor.getInt(cursor.getColumnIndexOrThrow("removeConnectionWhenBondRemovedByUser")) > 0;
        byte[] connectionDelayFilters = cursor.getBlob(cursor.getColumnIndexOrThrow("connectionDelayFilters"));
        if (connectionDelayFilters != null) {
            config.connectionDelayFilters = SafeParcelableSerializer.deserializeFromBytes(connectionDelayFilters, ConnectionDelayFilters.CREATOR);
        }
        config.maxSupportedRemoteAndroidSdkVersion = cursor.getInt(cursor.getColumnIndexOrThrow("maxSupportedRemoteAndroidSdkVersion"));
        config.runtimeType = cursor.getInt(cursor.getColumnIndexOrThrow("runtimeType"));
        config.skipConnectingIfDeviceNotBonded = cursor.getInt(cursor.getColumnIndexOrThrow("skipConnectingIfNotBonded")) > 0;
        return config;
    }

    public ConnectionConfiguration getConfiguration(String name) {
        Cursor cursor = getReadableDatabase().query(TABLE_NAME, null, BY_NAME, new String[]{name}, null, null, null);
        ConnectionConfiguration config = null;
        if (cursor != null) {
            if (cursor.moveToNext())
                config = configFromCursor(cursor);
            cursor.close();
        }
        return config;
    }

    public void putConfiguration(ConnectionConfiguration config) {
        putConfiguration(config, null);
    }

    public void putConfiguration(ConnectionConfiguration config, String oldNodeId) {
        ContentValues contentValues = new ContentValues();
        if (config.name != null) {
            contentValues.put("name", config.name);
        } else if (config.role == 2) {
            contentValues.put("name", "server");
        } else {
            contentValues.put("name", "NULL_STRING");
        }
        if (config.address != null) {
            contentValues.put("pairedBtAddress", config.address);
        } else {
            contentValues.put("pairedBtAddress", "NULL_STRING");
        }
        contentValues.put("connectionType", config.type);
        contentValues.put("role", config.role);
        contentValues.put("connectionEnabled", true);
        contentValues.put("nodeId", config.nodeId);
        contentValues.put("packageName", config.packageName);
        if (config.allowedConfigPackages != null) {
            contentValues.put("allowedConfigPackages", TextUtils.join(",", config.allowedConfigPackages));
        }
        contentValues.put("isMigrating", config.migrating);
        contentValues.put("dataItemSyncEnabled", config.dataItemSyncEnabled);
        if (config.connectionRestrictions != null) {
            contentValues.put("restrictions", SafeParcelableSerializer.serializeToBytes(config.connectionRestrictions));
        }
        contentValues.put("removeConnectionWhenBondRemovedByUser", config.removeConnectionWhenBondRemovedByUser);
        if (config.connectionDelayFilters != null) {
            contentValues.put("connectionDelayFilters", SafeParcelableSerializer.serializeToBytes(config.connectionDelayFilters));
        }
        contentValues.put("maxSupportedRemoteAndroidSdkVersion", config.maxSupportedRemoteAndroidSdkVersion);
        contentValues.put("runtimeType", config.runtimeType);
        contentValues.put("skipConnectingIfNotBonded", config.skipConnectingIfDeviceNotBonded);
        if (oldNodeId == null) {
            getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        } else {
            getWritableDatabase().update(TABLE_NAME, contentValues, "nodeId=?", new String[]{oldNodeId});
        }
    }

    public ConnectionConfiguration[] getAllConfigurations() {
        Cursor cursor = getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
        if (cursor != null) {
            List<ConnectionConfiguration> configurations = new ArrayList<ConnectionConfiguration>();
            while (cursor.moveToNext()) {
                configurations.add(configFromCursor(cursor));
            }
            cursor.close();
            return configurations.toArray(new ConnectionConfiguration[configurations.size()]);
        } else {
            return null;
        }
    }

    public void setEnabledState(String name, boolean enabled) {
        getWritableDatabase().execSQL("UPDATE connectionConfigurations SET connectionEnabled=? WHERE name=?", new String[]{enabled ? "1" : "0", name});
    }

    public int deleteConfiguration(String name) {
        return getWritableDatabase().delete(TABLE_NAME, BY_NAME, new String[]{name});
    }
}
