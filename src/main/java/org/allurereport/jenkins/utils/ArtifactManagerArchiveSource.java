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

import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link AllureReportArchiveSource} implementation that reads from an artifact stored via
 * Jenkins {@link ArtifactManager} / {@link VirtualFile}.
 *
 * <p>This implementation does <em>not</em> require the archive to be present as a local
 * file on the Jenkins controller—it delegates all I/O to the {@link VirtualFile} API, which
 * may transparently read from S3, Azure Blob Storage, or any other pluggable back-end.
 *
 * <p>Because {@link VirtualFile} streams are opened on demand and closed by the caller,
 * this class itself holds no persistent resources and {@link #close()} is a no-op.
 *
 * <p>A new instance is created for every report request, and each remote metadata call
 * ({@code exists}, {@code length}, {@code lastModified}) is a round trip to the storage
 * back-end. The archive's metadata is therefore cached per build for a short time.
 */
public final class ArtifactManagerArchiveSource implements AllureReportArchiveSource {

    private static final Logger LOGGER = Logger.getLogger(ArtifactManagerArchiveSource.class.getName());
    private static final int MAX_WARNED_FALLBACKS = 256;
    private static final Map<String, Boolean> WARNED_FALLBACKS =
            new LinkedHashMap<>(MAX_WARNED_FALLBACKS, 0.75F, true);

    private static final long METADATA_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_CACHED_ARCHIVES = 64;
    private static final Map<String, ArchiveMetadata> ARCHIVE_METADATA =
            new LinkedHashMap<>(MAX_CACHED_ARCHIVES, 0.75F, true);

    private final Run<?, ?> run;

    private VirtualFile artifactRoot;

    public ArtifactManagerArchiveSource(final Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            return false;
        }
        return archiveMetadata(root) != null;
    }

    @Override
    public InputStream openEntry(final String entryPath) throws IOException, InterruptedException {

        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            throw new NoSuchElementException("Artifact root not available for run: " + run.getFullDisplayName());
        }

        final ArchiveMetadata metadata = archiveMetadata(root);
        if (metadata == null || metadata.mayHaveDirectChild(root, entryPath)) {
            final VirtualFile directChild = root.child(entryPath);
            if (directChild.exists()) {
                return directChild.open();
            }
        }

        if (metadata == null) {
            throw new NoSuchElementException("allure-report.zip not found in artifact store for run: "
                    + run.getFullDisplayName());
        }
        final VirtualFile zipBlob = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        try {
            return RemoteZipArchive.openEntry(zipBlob, run.getExternalizableId(),
                    metadata.size, metadata.lastModified, entryPath);
        } catch (RemoteZipAccessException exception) {
            warnAboutSequentialFallback(exception);
            return ZipEntryInputStream.open(zipBlob.open(), entryPath);
        }
    }

    @Override
    public List<String> listEntries(final String prefix) throws IOException, InterruptedException {
        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            return new ArrayList<>();
        }

        final VirtualFile prefixDir = root.child(prefix);
        if (prefixDir.exists() && prefixDir.isDirectory()) {
            final List<String> result = new ArrayList<>();
            collectEntries(prefixDir, prefix, result);
            return result;
        }

        final ArchiveMetadata metadata = archiveMetadata(root);
        if (metadata == null) {
            return new ArrayList<>();
        }
        final VirtualFile zipBlob = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        try {
            return RemoteZipArchive.listEntries(zipBlob, run.getExternalizableId(),
                    metadata.size, metadata.lastModified, prefix);
        } catch (RemoteZipAccessException exception) {
            warnAboutSequentialFallback(exception);
            return ZipEntryInputStream.listEntries(zipBlob.open(), prefix);
        }
    }

    @Override
    @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
    public void close() {
    }

    private VirtualFile getArtifactRoot() {
        if (artifactRoot == null) {
            artifactRoot = run.getArtifactManager().root();
        }
        return artifactRoot;
    }

    /**
     * Returns the cached metadata of this build's {@code allure-report.zip}, loading it when absent or
     * expired. A missing archive is not cached, so a report that is still being archived appears as
     * soon as the upload completes.
     */
    private ArchiveMetadata archiveMetadata(final VirtualFile root) throws IOException {
        final String key = run.getExternalizableId() + '\n' + run.getStartTimeInMillis();
        final long now = System.currentTimeMillis();
        synchronized (ARCHIVE_METADATA) {
            final ArchiveMetadata cached = ARCHIVE_METADATA.get(key);
            if (cached != null && now - cached.loadedAt < METADATA_TTL_MILLIS) {
                return cached;
            }
        }
        final VirtualFile zip = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        if (!zip.exists()) {
            return null;
        }
        final ArchiveMetadata loaded = new ArchiveMetadata(zip.length(), zip.lastModified(), now);
        synchronized (ARCHIVE_METADATA) {
            ARCHIVE_METADATA.put(key, loaded);
            if (ARCHIVE_METADATA.size() > MAX_CACHED_ARCHIVES) {
                ARCHIVE_METADATA.remove(ARCHIVE_METADATA.keySet().iterator().next());
            }
        }
        return loaded;
    }

    private void warnAboutSequentialFallback(final RemoteZipAccessException exception) {
        final String reason = safeFailureReason(exception);
        final String warningKey = run.getRootDir().getAbsolutePath()
                + '\n' + run.getStartTimeInMillis() + '\n' + reason;
        if (!markFallbackWarning(warningKey)) {
            return;
        }

        LOGGER.log(Level.WARNING, "[Allure] Cannot use HTTP byte ranges for allure-report.zip of build "
                + run.getExternalizableId() + ": " + reason
                + ". Falling back to sequential ZIP streaming. Each report asset request may read the archive "
                + "from the beginning, so the report can load slowly. Verify that the artifact storage and any "
                + "reverse proxy return HTTP 206 and a valid Content-Range header for Range requests. This warning "
                + "is logged once per build and failure reason.");
    }

    private static boolean markFallbackWarning(final String warningKey) {
        synchronized (WARNED_FALLBACKS) {
            if (WARNED_FALLBACKS.get(warningKey) != null) {
                return false;
            }
            WARNED_FALLBACKS.put(warningKey, Boolean.TRUE);
            if (WARNED_FALLBACKS.size() > MAX_WARNED_FALLBACKS) {
                final String oldestKey = WARNED_FALLBACKS.keySet().iterator().next();
                WARNED_FALLBACKS.remove(oldestKey);
            }
            return true;
        }
    }

    private static String safeFailureReason(final RemoteZipAccessException exception) {
        String reason = null;
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof RemoteZipAccessException
                    && cause.getMessage() != null
                    && !cause.getMessage().isBlank()) {
                reason = cause.getMessage();
            }
            cause = cause.getCause();
        }
        return reason == null || reason.isBlank() ? "unknown byte-range access error" : reason;
    }

    private static void collectEntries(final VirtualFile dir,
                                       final String currentPath,
                                       final List<String> result)
            throws IOException {
        for (VirtualFile child : dir.list()) {
            final String childPath = currentPath + "/" + child.getName();
            if (child.isDirectory()) {
                collectEntries(child, childPath, result);
            } else {
                result.add(childPath);
            }
        }
    }

    private static final class ArchiveMetadata {

        private final long size;
        private final long lastModified;
        private final long loadedAt;
        private final Map<String, Boolean> directTopLevelEntries = new ConcurrentHashMap<>();

        ArchiveMetadata(final long size, final long lastModified, final long loadedAt) {
            this.size = size;
            this.lastModified = lastModified;
            this.loadedAt = loadedAt;
        }

        /**
         * Checks once per top-level directory whether report files were also archived unpacked,
         * so entries outside such a directory skip the per-file {@code exists} round trip.
         */
        boolean mayHaveDirectChild(final VirtualFile root, final String entryPath) throws IOException {
            final int slash = entryPath.indexOf('/');
            final String topLevel = slash > 0 ? entryPath.substring(0, slash) : entryPath;
            final Boolean cached = directTopLevelEntries.get(topLevel);
            if (cached != null) {
                return cached;
            }
            final boolean exists = root.child(topLevel).exists();
            directTopLevelEntries.put(topLevel, exists);
            return exists;
        }
    }
}
