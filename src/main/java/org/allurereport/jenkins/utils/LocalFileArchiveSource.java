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

import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@link AllureReportArchiveSource} implementation that reads from a local
 * {@code allure-report.zip} file accessible via a {@link FilePath} on the Jenkins master.
 *
 * <p>The underlying {@link ZipFile} is opened lazily on the first call to
 * {@link #openEntry(String)} or {@link #listEntries(String)} and is closed when
 * {@link #close()} is called.
 */
public final class LocalFileArchiveSource implements AllureReportArchiveSource {

    private final FilePath archivePath;

    private ZipFile zipFile;

    public LocalFileArchiveSource(final FilePath archivePath) {
        this.archivePath = archivePath;
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return archivePath.exists();
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public InputStream openEntry(final String entryPath) throws IOException {
        final ZipFile zip = getOrOpenZip();
        final ZipEntry entry = zip.getEntry(entryPath);
        if (entry == null) {
            throw new NoSuchElementException("Entry not found in archive: " + entryPath);
        }
        return zip.getInputStream(entry);
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public List<String> listEntries(final String prefix) throws IOException {
        final ZipFile zip = getOrOpenZip();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        final List<String> result = new ArrayList<>();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(prefix) && !entry.isDirectory()) {
                result.add(entry.getName());
            }
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
            zipFile = null;
        }
    }

    private ZipFile getOrOpenZip() throws IOException {
        if (zipFile == null) {
            zipFile = new ZipFile(archivePath.getRemote());
        }
        return zipFile;
    }
}
