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
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.allurereport.jenkins.AllureReportPublisherDescriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link AllureReportArchiveSource} that reads from a remote artifact store via
 * {@link VirtualFile}. On first access, downloads the zip to the local artifacts
 * directory ({@code <run.getArtifactsDir()>/allure-report.zip}) so that subsequent
 * requests are served by {@link LocalFileArchiveSource} via the
 * {@link FallbackArchiveSource} chain — identical behavior to a non-S3 setup.
 *
 * <p>The local copy is naturally cleaned up by Jenkins' build discarder along with
 * the rest of the build's artifacts.
 */
public final class ArtifactManagerArchiveSource implements AllureReportArchiveSource {

    private static final Logger LOGGER = Logger.getLogger(ArtifactManagerArchiveSource.class.getName());

    /**
     * Guards the download-and-open step against concurrent first views of the same build.
     * A fresh {@link ArtifactManagerArchiveSource} is created per HTTP request, but browsers
     * fire many parallel asset requests, so several instances can race to materialize the same
     * {@code <artifactsDir>/allure-report.zip}. Keyed by the destination path so unrelated builds
     * never contend; entries are only ever added, which is fine for the small, bounded set of
     * builds browsed in a session.
     */
    private static final ConcurrentHashMap<Path, Object> DOWNLOAD_LOCKS = new ConcurrentHashMap<>();

    private final Run<?, ?> run;
    private VirtualFile artifactRoot;
    private ZipFile localZipFile;

    public ArtifactManagerArchiveSource(final Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            return false;
        }
        final VirtualFile zip = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        return zip.exists();
    }

    @Override
    public InputStream openEntry(final String entryPath) throws IOException, InterruptedException {
        final String runId = run.getExternalizableId();

        final InputStream cached = ReportEntryCache.getInstance().get(runId, entryPath);
        if (cached != null) {
            return cached;
        }

        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            throw new NoSuchElementException("Artifact root not available for run: " + run.getFullDisplayName());
        }

        final VirtualFile directChild = root.child(entryPath);
        if (directChild.exists()) {
            return directChild.open();
        }

        final VirtualFile zipBlob = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        if (!zipBlob.exists()) {
            throw new NoSuchElementException(
                    "allure-report.zip not found in artifact store for run: " + run.getFullDisplayName());
        }

        if (isLocalCacheApplicable(zipBlob)) {
            final ZipFile zip = getOrDownloadToArtifactsDir(zipBlob);
            final ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) {
                throw new NoSuchElementException("Entry not found in archive: " + entryPath);
            }
            try (InputStream is = zip.getInputStream(entry)) {
                final byte[] data = is.readAllBytes();
                ReportEntryCache.getInstance().put(runId, entryPath, data);
                return new ByteArrayInputStream(data);
            }
        }

        return ZipEntryInputStream.open(zipBlob.open(), entryPath);
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

        final VirtualFile zipBlob = root.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        if (!zipBlob.exists()) {
            return new ArrayList<>();
        }

        if (isLocalCacheApplicable(zipBlob)) {
            final ZipFile zip = getOrDownloadToArtifactsDir(zipBlob);
            final List<String> result = new ArrayList<>();
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith(prefix) && !entry.isDirectory()) {
                    result.add(entry.getName());
                }
            }
            return result;
        }

        return ZipEntryInputStream.listEntries(zipBlob.open(), prefix);
    }

    @Override
    public void close() throws IOException {
        if (localZipFile != null) {
            localZipFile.close();
            localZipFile = null;
        }
    }

    private boolean isLocalCacheApplicable(final VirtualFile zipBlob) throws IOException {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return false;
        }
        final AllureReportPublisherDescriptor descriptor =
                jenkins.getDescriptorByType(AllureReportPublisherDescriptor.class);
        if (descriptor == null || !descriptor.isLocalCacheEnabled()) {
            return false;
        }
        final long thresholdBytes = descriptor.getLocalCacheThresholdMb() * 1024L * 1024L;
        return zipBlob.length() >= thresholdBytes;
    }

    @SuppressWarnings("PMD.CloseResource")
    private ZipFile getOrDownloadToArtifactsDir(final VirtualFile zipBlob)
            throws IOException, InterruptedException {
        // This instance is confined to a single request, but multiple instances (one per
        // parallel asset request) can target the same file; serialize per destination path.
        if (localZipFile != null) {
            return localZipFile;
        }

        final Path localPath = run.getArtifactsDir().toPath()
                .resolve(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);

        synchronized (DOWNLOAD_LOCKS.computeIfAbsent(localPath.toAbsolutePath(), k -> new Object())) {
            if (localZipFile != null) {
                return localZipFile;
            }
            if (Files.exists(localPath)) {
                LOGGER.log(Level.FINE, "Using existing local copy at {0}", localPath);
                localZipFile = new ZipFile(localPath.toFile());
                return localZipFile;
            }

            LOGGER.log(Level.INFO, "Downloading allure-report.zip from remote storage to {0} for run {1}",
                    new Object[]{localPath, run.getExternalizableId()});

            Files.createDirectories(localPath.getParent());
            final Path tmpFile = Files.createTempFile(localPath.getParent(), "allure-report-", ".zip.tmp");
            try (InputStream is = zipBlob.open()) {
                Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmpFile, localPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            localZipFile = new ZipFile(localPath.toFile());
            return localZipFile;
        }
    }

    private VirtualFile getArtifactRoot() {
        if (artifactRoot == null) {
            final ArtifactManager manager = run.getArtifactManager();
            if (manager == null) {
                return null;
            }
            artifactRoot = manager.root();
        }
        return artifactRoot;
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
}
