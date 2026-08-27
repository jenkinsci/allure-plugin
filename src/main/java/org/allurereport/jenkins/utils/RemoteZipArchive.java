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

import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class RemoteZipArchive {

    private static final int MAX_CACHED_ARCHIVES = 8;
    private static final String INDEX_FAILURE = "Unable to read the remote ZIP index";
    private static final String RANGE_FAILURE = "Unable to use byte ranges for the remote ZIP";
    private static final Map<String, CompletableFuture<RemoteZipIndex>> INDEX_CACHE =
            new LinkedHashMap<>(MAX_CACHED_ARCHIVES, 0.75F, true);

    private RemoteZipArchive() {
    }

    static InputStream openEntry(final VirtualFile file,
                                 final String archiveId,
                                 final String entryPath) throws IOException {
        try {
            final long archiveSize = file.length();
            final RemoteZipIndex index = index(file, archiveSize, cacheKey(file, archiveId, archiveSize));
            final RemoteZipIndex.Entry entry = index.get(entryPath);
            if (entry == null) {
                throw new NoSuchElementException("Entry not found in remote ZIP: " + entryPath);
            }
            return RemoteZipEntryInputStream.open(file, archiveSize, entry);
        } catch (IOException exception) {
            throw new RemoteZipAccessException(RANGE_FAILURE, exception);
        }
    }

    static List<String> listEntries(final VirtualFile file,
                                    final String archiveId,
                                    final String prefix) throws IOException {
        try {
            final long archiveSize = file.length();
            return index(file, archiveSize, cacheKey(file, archiveId, archiveSize)).list(prefix);
        } catch (IOException exception) {
            throw new RemoteZipAccessException(RANGE_FAILURE, exception);
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static RemoteZipIndex index(final VirtualFile file,
                                        final long archiveSize,
                                        final String cacheKey) throws RemoteZipAccessException {
        final CompletableFuture<RemoteZipIndex> candidate = new CompletableFuture<>();
        final CompletableFuture<RemoteZipIndex> future = cachedFuture(cacheKey, candidate);
        if (future == candidate) {
            loadIndex(file, archiveSize, cacheKey, future);
        }
        return awaitIndex(future);
    }

    private static CompletableFuture<RemoteZipIndex> cachedFuture(
            final String cacheKey,
            final CompletableFuture<RemoteZipIndex> candidate) {
        synchronized (INDEX_CACHE) {
            final CompletableFuture<RemoteZipIndex> cached = INDEX_CACHE.get(cacheKey);
            if (cached == null) {
                INDEX_CACHE.put(cacheKey, candidate);
                trimCache();
                return candidate;
            }
            return cached;
        }
    }

    private static void loadIndex(final VirtualFile file,
                                  final long archiveSize,
                                  final String cacheKey,
                                  final CompletableFuture<RemoteZipIndex> future) {
        try {
            future.complete(RemoteZipIndex.load(file, archiveSize));
        } catch (RemoteZipAccessException exception) {
            future.completeExceptionally(exception);
            removeFailedIndex(cacheKey, future);
        } catch (IOException | RuntimeException exception) {
            future.completeExceptionally(new RemoteZipAccessException(INDEX_FAILURE, exception));
            removeFailedIndex(cacheKey, future);
        }
    }

    private static void removeFailedIndex(final String cacheKey,
                                          final CompletableFuture<RemoteZipIndex> future) {
        synchronized (INDEX_CACHE) {
            INDEX_CACHE.remove(cacheKey, future);
        }
    }

    private static RemoteZipIndex awaitIndex(final CompletableFuture<RemoteZipIndex> future)
            throws RemoteZipAccessException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RemoteZipAccessException("Interrupted while reading the remote ZIP index", exception);
        } catch (ExecutionException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof RemoteZipAccessException) {
                throw (RemoteZipAccessException) cause;
            }
            throw new RemoteZipAccessException(INDEX_FAILURE, exception);
        }
    }

    private static String cacheKey(final VirtualFile file,
                                   final String archiveId,
                                   final long archiveSize) throws IOException {
        return archiveId + '\n' + file.toURI() + '\n' + archiveSize + '\n' + file.lastModified();
    }

    private static void trimCache() {
        final Iterator<String> keys = INDEX_CACHE.keySet().iterator();
        while (INDEX_CACHE.size() > MAX_CACHED_ARCHIVES && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }
}
