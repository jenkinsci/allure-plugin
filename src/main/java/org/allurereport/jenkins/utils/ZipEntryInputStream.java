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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility for reading entries from a ZIP archive provided as a raw {@link InputStream}.
 *
 * <p>This is used as a fallback when the archive is not available as a local file
 * (e.g. when reading from a remote artifact store via {@link jenkins.util.VirtualFile}).
 */
final class ZipEntryInputStream {

    private ZipEntryInputStream() {
    }

    static InputStream open(final InputStream zipStream, final String entryPath) throws IOException {
        final ZipInputStream zis = new ZipInputStream(zipStream);
        ZipEntry entry = zis.getNextEntry();
        while (entry != null) {
            if (entryPath.equals(entry.getName())) {
                return zis;
            }
            entry = zis.getNextEntry();
        }
        zis.close();
        throw new NoSuchElementException("Entry not found in ZIP stream: " + entryPath);
    }

    static List<String> listEntries(final InputStream zipStream, final String prefix) throws IOException {
        final List<String> result = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.getName().startsWith(prefix)) {
                    result.add(entry.getName());
                }
                entry = zis.getNextEntry();
            }
        }
        return result;
    }
}
