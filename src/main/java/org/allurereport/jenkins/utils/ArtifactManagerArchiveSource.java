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
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link AllureReportArchiveSource} implementation that reads from an artifact stored via
 * Jenkins {@link ArtifactManager} / {@link VirtualFile}.
 *
 * <p>This implementation does <em>not</em> require the archive to be present as a local
 * file on the master node Ã¢ it delegates all I/O to the {@link VirtualFile} API, which
 * may transparently read from S3, Azure Blob Storage, or any other pluggable back-end.
 *
 * <p>Because {@link VirtualFile} streams are opened on demand and closed by the caller,
 * this class itself holds no persistent resources and {@link #close()} is a no-op.
 */
public final class ArtifactManagerArchiveSource implements AllureReportArchiveSource {

    private static final String ALLURE_REPORT_ZIP = "allure-report.zip";

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
        final VirtualFile zip = root.child(ALLURE_REPORT_ZIP);
        return zip.exists();
    }

    @Override
    public InputStream openEntry(final String entryPath) throws IOException, InterruptedException {

        final VirtualFile root = getArtifactRoot();
        if (root == null) {
            throw new NoSuchElementException("Artifact root not available for run: " + run.getFullDisplayName());
        }

        final VirtualFile directChild = root.child(entryPath);
        if (directChild.exists()) {
            return directChild.open();
        }

        final VirtualFile zipBlob = root.child(ALLURE_REPORT_ZIP);
        if (!zipBlob.exists()) {
            throw new NoSuchElementException("allure-report.zip not found in artifact store for run: "
                    + run.getFullDisplayName());
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

        final VirtualFile zipBlob = root.child(ALLURE_REPORT_ZIP);
        if (!zipBlob.exists()) {
            return new ArrayList<>();
        }
        return ZipEntryInputStream.listEntries(zipBlob.open(), prefix);
    }

    @Override
    @SuppressWarnings("PMD.UncommentedEmptyMethodBody")
    public void close() {
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
