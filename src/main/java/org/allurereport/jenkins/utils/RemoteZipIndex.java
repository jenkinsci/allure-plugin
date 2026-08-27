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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RemoteZipIndex {

    private final Map<String, Entry> entries;

    private RemoteZipIndex(final Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static RemoteZipIndex load(final VirtualFile file, final long size) throws IOException {
        if (size <= 0) {
            throw new RemoteZipAccessException("Remote ZIP has no readable content");
        }

        final HttpRangeReader reader = new HttpRangeReader(file, size);
        final Map<String, Entry> entries = new LinkedHashMap<>();
        try (SeekableByteChannel channel = new PagedChannel(reader, size);
             ZipFile zip = ZipFile.builder()
                     .setSeekableByteChannel(channel)
                     .setIgnoreLocalFileHeader(true)
                     .get()) {
            final Enumeration<ZipArchiveEntry> zipEntries = zip.getEntries();
            while (zipEntries.hasMoreElements()) {
                final ZipArchiveEntry zipEntry = zipEntries.nextElement();
                if (!zipEntry.isDirectory()) {
                    entries.putIfAbsent(zipEntry.getName(), new Entry(zipEntry));
                }
            }
        }
        return new RemoteZipIndex(entries);
    }

    Entry get(final String path) {
        return entries.get(path);
    }

    List<String> list(final String prefix) {
        final List<String> result = new ArrayList<>();
        for (String name : entries.keySet()) {
            if (name.startsWith(prefix)) {
                result.add(name);
            }
        }
        return result;
    }

    static final class Entry {

        private static final int LOCAL_HEADER_LENGTH = 30;
        private static final int LOCAL_HEADER_SIGNATURE = 0x04034b50;
        private static final long UNKNOWN_OFFSET = -1L;

        private final String name;
        private final long localHeaderOffset;
        private final long compressedSize;
        private final long size;
        private final long crc;
        private final int method;
        private final boolean encrypted;
        private long dataOffset = UNKNOWN_OFFSET;

        Entry(final ZipArchiveEntry entry) {
            this.name = entry.getName();
            this.localHeaderOffset = entry.getLocalHeaderOffset();
            this.compressedSize = entry.getCompressedSize();
            this.size = entry.getSize();
            this.crc = entry.getCrc();
            this.method = entry.getMethod();
            this.encrypted = entry.getGeneralPurposeBit().usesEncryption();
        }

        String getName() {
            return name;
        }

        long getCompressedSize() {
            return compressedSize;
        }

        long getSize() {
            return size;
        }

        long getCrc() {
            return crc;
        }

        int getMethod() {
            return method;
        }

        boolean isEncrypted() {
            return encrypted;
        }

        long resolveDataOffset(final HttpRangeReader reader,
                               final long archiveSize) throws IOException {
            synchronized (this) {
                if (dataOffset == UNKNOWN_OFFSET) {
                    dataOffset = readDataOffset(reader, archiveSize);
                }
                return dataOffset;
            }
        }

        private long readDataOffset(final HttpRangeReader reader,
                                    final long archiveSize) throws IOException {
            if (localHeaderOffset < 0 || compressedSize < 0 || size < 0) {
                throw new RemoteZipAccessException("Remote ZIP entry has invalid size metadata");
            }
            final byte[] header = reader.readRange(localHeaderOffset, LOCAL_HEADER_LENGTH);
            if (littleEndianInt(header, 0) != LOCAL_HEADER_SIGNATURE) {
                throw new RemoteZipAccessException("Remote ZIP entry has an invalid local header");
            }
            final int nameLength = littleEndianShort(header, 26);
            final int extraLength = littleEndianShort(header, 28);
            final long resolved = localHeaderOffset + LOCAL_HEADER_LENGTH + nameLength + extraLength;
            if (resolved < localHeaderOffset
                    || resolved > archiveSize
                    || compressedSize > archiveSize - resolved) {
                throw new RemoteZipAccessException("Remote ZIP entry data is outside the archive");
            }
            return resolved;
        }

        private static int littleEndianInt(final byte[] bytes, final int offset) {
            return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
        }

        private static int littleEndianShort(final byte[] bytes, final int offset) {
            return Short.toUnsignedInt(ByteBuffer.wrap(bytes, offset, Short.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getShort());
        }
    }

    private static final class PagedChannel implements SeekableByteChannel {

        private static final int PAGE_SIZE = 64 * 1024;

        private final HttpRangeReader reader;
        private final long archiveSize;
        private final Map<Long, byte[]> pages = new HashMap<>();
        private long currentPosition;
        private boolean open = true;

        PagedChannel(final HttpRangeReader reader, final long size) {
            this.reader = reader;
            this.archiveSize = size;
        }

        @Override
        public int read(final ByteBuffer target) throws IOException {
            ensureOpen();
            if (!target.hasRemaining()) {
                return 0;
            }
            if (currentPosition >= archiveSize) {
                return -1;
            }
            final int initialRemaining = target.remaining();
            while (target.hasRemaining() && currentPosition < archiveSize) {
                final long pageNumber = currentPosition / PAGE_SIZE;
                final byte[] page = page(pageNumber);
                final int pageOffset = (int) (currentPosition % PAGE_SIZE);
                final int length = Math.min(target.remaining(), page.length - pageOffset);
                target.put(page, pageOffset, length);
                currentPosition += length;
            }
            return initialRemaining - target.remaining();
        }

        @Override
        public int write(final ByteBuffer source) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public long position() throws IOException {
            ensureOpen();
            return currentPosition;
        }

        @Override
        public SeekableByteChannel position(final long newPosition) throws IOException {
            ensureOpen();
            if (newPosition < 0) {
                throw new IllegalArgumentException("Position must not be negative");
            }
            currentPosition = newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            ensureOpen();
            return archiveSize;
        }

        @Override
        public SeekableByteChannel truncate(final long newSize) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            pages.clear();
        }

        private byte[] page(final long pageNumber) throws IOException {
            final byte[] cached = pages.get(pageNumber);
            if (cached != null) {
                return cached;
            }
            final long start = pageNumber * PAGE_SIZE;
            final int length = (int) Math.min(PAGE_SIZE, archiveSize - start);
            final byte[] loaded = reader.readRange(start, length);
            pages.put(pageNumber, loaded);
            return loaded;
        }

        private void ensureOpen() throws ClosedChannelException {
            if (!open) {
                throw new ClosedChannelException();
            }
        }
    }
}
