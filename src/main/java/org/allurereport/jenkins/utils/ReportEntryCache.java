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
    private static final boolean ACCESS_ORDER = true;
    private static final String KEY_SEPARATOR = "\0";

    private static final ReportEntryCache INSTANCE = new ReportEntryCache(DEFAULT_MAX_BYTES);

    private final long maxBytes;
    private long currentBytes;
    private final Map<String, byte[]> cache;

    ReportEntryCache(final long maxBytes) {
        this.maxBytes = maxBytes;
        this.currentBytes = 0;
        // access-order map so iteration in trimToSize() evicts least-recently-used entries first.
        // Eviction is driven solely by trimToSize() to keep currentBytes accounting in one place.
        this.cache = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER);
    }

    private static String key(final String runId, final String entryPath) {
        return runId + KEY_SEPARATOR + entryPath;
    }

    public static ReportEntryCache getInstance() {
        return INSTANCE;
    }

    public synchronized InputStream get(final String runId, final String entryPath) {
        final byte[] data = cache.get(key(runId, entryPath));
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
        final byte[] existing = cache.remove(key(runId, entryPath));
        if (existing != null) {
            currentBytes -= existing.length;
        }
        currentBytes += data.length;
        cache.put(key(runId, entryPath), data);
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
