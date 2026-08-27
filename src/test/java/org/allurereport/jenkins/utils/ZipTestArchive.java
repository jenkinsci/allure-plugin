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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ZipTestArchive {

    private ZipTestArchive() {
    }

    static byte[] createWithLargeLeadingEntry(final String largeEntryName,
                                              final int largeEntrySize,
                                              final String entryName,
                                              final String entryContent) throws IOException {
        final byte[] largeContent = new byte[largeEntrySize];
        final CRC32 crc = new CRC32();
        crc.update(largeContent);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            final ZipEntry largeEntry = new ZipEntry(largeEntryName);
            largeEntry.setMethod(ZipEntry.STORED);
            largeEntry.setSize(largeContent.length);
            largeEntry.setCompressedSize(largeContent.length);
            largeEntry.setCrc(crc.getValue());
            zip.putNextEntry(largeEntry);
            zip.write(largeContent);
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(entryContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
