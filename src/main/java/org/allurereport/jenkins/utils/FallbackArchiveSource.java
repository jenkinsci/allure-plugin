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
import java.util.List;

final class FallbackArchiveSource implements AllureReportArchiveSource {

    private final AllureReportArchiveSource primary;
    private final AllureReportArchiveSource fallback;

    FallbackArchiveSource(final AllureReportArchiveSource primary,
                          final AllureReportArchiveSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return primary.exists() || fallback.exists();
    }

    @Override
    public InputStream openEntry(final String entryPath) throws IOException, InterruptedException {
        if (primary.exists()) {
            return primary.openEntry(entryPath);
        }
        return fallback.openEntry(entryPath);
    }

    @Override
    public List<String> listEntries(final String prefix) throws IOException, InterruptedException {
        if (primary.exists()) {
            return primary.listEntries(prefix);
        }
        return fallback.listEntries(prefix);
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
