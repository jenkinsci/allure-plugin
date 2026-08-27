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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

final class RemoteZipEntryInputStream {

    private RemoteZipEntryInputStream() {
    }

    @SuppressWarnings("PMD.CloseResource")
    static InputStream open(final VirtualFile file,
                            final long archiveSize,
                            final RemoteZipIndex.Entry entry) throws IOException {
        if (entry.isEncrypted()) {
            throw new RemoteZipAccessException("Encrypted remote ZIP entries are not supported");
        }
        if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
            throw new RemoteZipAccessException("Remote ZIP entry uses an unsupported compression method");
        }

        final HttpRangeReader reader = new HttpRangeReader(file, archiveSize);
        final long dataOffset = entry.resolveDataOffset(reader, archiveSize);
        final InputStream compressed = reader.openRange(dataOffset, entry.getCompressedSize());
        try {
            final InputStream content = entry.getMethod() == ZipEntry.DEFLATED
                    ? new RawInflaterInputStream(compressed) : compressed;
            return new ValidatingInputStream(content, entry);
        } catch (RuntimeException exception) {
            compressed.close();
            throw exception;
        }
    }

    private static final class RawInflaterInputStream extends InflaterInputStream {

        private final Inflater rawInflater;

        RawInflaterInputStream(final InputStream input) {
            this(input, new Inflater(true));
        }

        private RawInflaterInputStream(final InputStream input, final Inflater inflater) {
            super(input, inflater);
            this.rawInflater = inflater;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                rawInflater.end();
            }
        }
    }

    private static final class ValidatingInputStream extends FilterInputStream {

        private final String entryName;
        private final long expectedSize;
        private final long expectedCrc;
        private final CRC32 crc = new CRC32();
        private long bytesRead;
        private boolean validated;

        ValidatingInputStream(final InputStream input, final RemoteZipIndex.Entry entry) {
            super(input);
            this.entryName = entry.getName();
            this.expectedSize = entry.getSize();
            this.expectedCrc = entry.getCrc();
        }

        @Override
        public int read() throws IOException {
            final int result = super.read();
            if (result < 0) {
                validateEnd();
            } else {
                crc.update(result);
                bytesRead++;
                validateMaximumSize();
            }
            return result;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int result = super.read(buffer, offset, length);
            if (result < 0) {
                validateEnd();
            } else if (result > 0) {
                crc.update(buffer, offset, result);
                bytesRead += result;
                validateMaximumSize();
            }
            return result;
        }

        private void validateMaximumSize() throws ZipException {
            if (bytesRead > expectedSize) {
                throw new ZipException("Remote ZIP entry exceeds its declared size: " + entryName);
            }
        }

        private void validateEnd() throws ZipException {
            if (validated) {
                return;
            }
            validated = true;
            if (bytesRead != expectedSize) {
                throw new ZipException("Remote ZIP entry has an unexpected size: " + entryName);
            }
            if (expectedCrc >= 0 && crc.getValue() != expectedCrc) {
                throw new ZipException("Remote ZIP entry failed its CRC check: " + entryName);
            }
        }
    }
}
