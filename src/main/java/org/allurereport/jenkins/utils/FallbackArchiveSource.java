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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

final class FallbackArchiveSource implements AllureReportArchiveSource {

    private final AllureReportArchiveSource primary;
    private final AllureReportArchiveSource fallback;

    FallbackArchiveSource(final AllureReportArchiveSource primary,
                          final AllureReportArchiveSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public AllureReportArchiveSource activeSource() throws IOException, InterruptedException {
        if (primary.exists()) {
            return primary;
        }
        if (fallback.exists()) {
            return fallback;
        }
        return null;
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return activeSource() != null;
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public InputStream openEntry(final String entryPath) throws IOException, InterruptedException {
        final AllureReportArchiveSource active = activeSource();
        if (active != null) {
            return active.openEntry(entryPath);
        }
        throw new NoSuchElementException("No archive source available for entry: " + entryPath);
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public List<String> listEntries(final String prefix) throws IOException, InterruptedException {
        final AllureReportArchiveSource active = activeSource();
        if (active != null) {
            return active.listEntries(prefix);
        }
        return Collections.emptyList();
    }

    @Override
    public void close() throws IOException {
        IOException firstException = null;
        try {
            primary.close();
        } catch (IOException ex) {
            firstException = ex;
        }
        try {
            fallback.close();
        } catch (IOException ex) {
            if (firstException != null) {
                firstException.addSuppressed(ex);
            } else {
                firstException = ex;
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }
}
