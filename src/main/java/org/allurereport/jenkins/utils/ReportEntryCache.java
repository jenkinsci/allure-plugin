/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache for decompressed report entry bytes, keyed by (runId, entryPath).
 */
public final class ReportEntryCache {

    private static final long DEFAULT_MAX_BYTES = 200L * 1024 * 1024;
    private static final int INITIAL_CAPACITY = 64;
    private static final float LOAD_FACTOR = 0.75f;

    private static final ReportEntryCache INSTANCE = new ReportEntryCache(DEFAULT_MAX_BYTES);

    private final long maxBytes;
    private long currentBytes;
    private final Map<String, byte[]> cache;

    ReportEntryCache(final long maxBytes) {
        this.maxBytes = maxBytes;
        this.currentBytes = 0;
        this.cache = new LinkedHashMap<String, byte[]>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, byte[]> eldest) {
                if (currentBytes > ReportEntryCache.this.maxBytes) {
                    currentBytes -= eldest.getValue().length;
                    return true;
                }
                return false;
            }
        };
    }

    public static ReportEntryCache getInstance() {
        return INSTANCE;
    }

    public synchronized InputStream get(final String runId, final String entryPath) {
        final String key = runId + "\0" + entryPath;
        final byte[] data = cache.get(key);
        if (data == null) {
            return null;
        }
        return new ByteArrayInputStream(data);
    }

    public synchronized void clear() {
        cache.clear();
        currentBytes = 0;
    }

    public synchronized void put(final String runId, final String entryPath, final byte[] data) {
        if (data.length > maxBytes) {
            return;
        }
        final String key = runId + "\0" + entryPath;
        final byte[] existing = cache.remove(key);
        if (existing != null) {
            currentBytes -= existing.length;
        }
        currentBytes += data.length;
        cache.put(key, data);
        trimToSize();
    }

    private void trimToSize() {
        final java.util.Iterator<Map.Entry<String, byte[]>> iterator = cache.entrySet().iterator();
        while (currentBytes > maxBytes && iterator.hasNext()) {
            final Map.Entry<String, byte[]> entry = iterator.next();
            currentBytes -= entry.getValue().length;
            iterator.remove();
        }
    }
}