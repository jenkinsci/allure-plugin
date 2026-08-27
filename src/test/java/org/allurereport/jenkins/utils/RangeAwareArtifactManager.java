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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only artifact manager whose archive can be read through either a full stream or HTTP byte ranges.
 */
final class RangeAwareArtifactManager extends ArtifactManager implements AutoCloseable {

    private static final String PATH_SEPARATOR = "/";
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String CONTENT_RANGE_HEADER = "Content-Range";
    private static final Pattern RANGE_HEADER = Pattern.compile("bytes=(\\d+)-(\\d+)");
    private static final String ARCHIVE_PATH = PATH_SEPARATOR
            + AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP;

    private final byte[] archive;
    private final boolean rangeSupported;
    private final AtomicLong fullStreamBytesRead = new AtomicLong();
    private final AtomicLong rangeBytesServed = new AtomicLong();
    private final AtomicInteger rangeRequests = new AtomicInteger();
    private final HttpServer server;
    private final URL archiveUrl;
    private final VirtualFile root;

    RangeAwareArtifactManager(final byte[] archive, final boolean rangeSupported) throws IOException {
        this.archive = archive.clone();
        this.rangeSupported = rangeSupported;
        this.server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        this.server.createContext(ARCHIVE_PATH, this::serveArchive);
        this.server.start();
        this.archiveUrl = new URL("http", LOOPBACK_HOST, server.getAddress().getPort(), ARCHIVE_PATH);
        this.root = new TestVirtualFile(this, FileKind.ROOT, "artifacts", null);
    }

    void install(final FreeStyleBuild build) throws Exception {
        final java.lang.reflect.Field artifactManager = Run.class.getDeclaredField("artifactManager");
        artifactManager.setAccessible(true);
        onLoad(build);
        artifactManager.set(build, this);
    }

    long getFullStreamBytesRead() {
        return fullStreamBytesRead.get();
    }

    long getRangeBytesServed() {
        return rangeBytesServed.get();
    }

    int getRangeRequests() {
        return rangeRequests.get();
    }

    int getArchiveSize() {
        return archive.length;
    }

    @Override
    public void onLoad(final Run<?, ?> run) {
    }

    @Override
    public void archive(final FilePath workspace,
                        final Launcher launcher,
                        final BuildListener listener,
                        final Map<String, String> artifacts) {
        throw new UnsupportedOperationException("Not used in tests");
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public VirtualFile root() {
        return root;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void serveArchive(final HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            final String range = exchange.getRequestHeaders().getFirst("Range");
            if (!rangeSupported || range == null) {
                if (range != null) {
                    rangeRequests.incrementAndGet();
                }
                exchange.sendResponseHeaders(200, archive.length);
                exchange.getResponseBody().write(archive);
                return;
            }

            rangeRequests.incrementAndGet();
            final Matcher matcher = RANGE_HEADER.matcher(range);
            if (!matcher.matches()) {
                sendRangeNotSatisfiable(exchange);
                return;
            }

            final long requestedStart = Long.parseLong(matcher.group(1));
            final long requestedEnd = Long.parseLong(matcher.group(2));
            if (requestedStart < 0 || requestedStart >= archive.length || requestedEnd < requestedStart) {
                sendRangeNotSatisfiable(exchange);
                return;
            }

            final int start = Math.toIntExact(requestedStart);
            final int end = Math.toIntExact(Math.min(requestedEnd, archive.length - 1L));
            final int length = end - start + 1;
            rangeBytesServed.addAndGet(length);
            exchange.getResponseHeaders().set(
                    CONTENT_RANGE_HEADER,
                    "bytes " + start + "-" + end + PATH_SEPARATOR + archive.length
            );
            exchange.sendResponseHeaders(206, length);
            exchange.getResponseBody().write(archive, start, length);
        } catch (IOException clientDisconnected) {
            // A range capability probe intentionally closes a full response as soon as it sees HTTP 200.
        } finally {
            exchange.close();
        }
    }

    private void sendRangeNotSatisfiable(final HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set(CONTENT_RANGE_HEADER, "bytes */" + archive.length);
        exchange.sendResponseHeaders(416, -1);
    }

    private enum FileKind {
        ROOT,
        ARCHIVE,
        MISSING
    }

    private static final class TestVirtualFile extends VirtualFile {

        private static final long serialVersionUID = 1L;

        private final RangeAwareArtifactManager manager;
        private final FileKind kind;
        private final String name;
        private final TestVirtualFile parent;

        TestVirtualFile(final RangeAwareArtifactManager manager,
                        final FileKind kind,
                        final String name,
                        final TestVirtualFile parent) {
            this.manager = manager;
            this.kind = kind;
            this.name = name;
            this.parent = parent;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public URI toURI() {
            return URI.create("memory:/allure-test/"
                    + kind.name().toLowerCase() + PATH_SEPARATOR + name);
        }

        @Override
        public URL toExternalURL() {
            return kind == FileKind.ARCHIVE ? manager.archiveUrl : null;
        }

        @Override
        public VirtualFile getParent() {
            return parent;
        }

        @Override
        public boolean isDirectory() {
            return kind == FileKind.ROOT;
        }

        @Override
        public boolean isFile() {
            return kind == FileKind.ARCHIVE;
        }

        @Override
        public boolean exists() {
            return kind != FileKind.MISSING;
        }

        @Override
        public VirtualFile[] list() {
            if (kind != FileKind.ROOT) {
                return new VirtualFile[0];
            }
            return new VirtualFile[]{child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP)};
        }

        @Override
        public VirtualFile child(final String childName) {
            if (kind == FileKind.ROOT
                    && AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP.equals(childName)) {
                return new TestVirtualFile(manager, FileKind.ARCHIVE, childName, this);
            }
            return new TestVirtualFile(manager, FileKind.MISSING, childName, this);
        }

        @Override
        public long length() {
            return kind == FileKind.ARCHIVE ? manager.archive.length : 0;
        }

        @Override
        public long lastModified() {
            return kind == FileKind.ARCHIVE ? 1L : 0L;
        }

        @Override
        public boolean canRead() {
            return exists();
        }

        @Override
        public InputStream open() throws IOException {
            if (kind != FileKind.ARCHIVE) {
                throw new FileNotFoundException(toString());
            }
            return new CountingInputStream(manager.archive, manager.fullStreamBytesRead);
        }
    }

    private static final class CountingInputStream extends FilterInputStream {

        private final AtomicLong bytesRead;

        CountingInputStream(final byte[] data, final AtomicLong bytesRead) {
            super(new java.io.ByteArrayInputStream(data));
            this.bytesRead = bytesRead;
        }

        @Override
        public int read() throws IOException {
            final int result = super.read();
            if (result >= 0) {
                bytesRead.incrementAndGet();
            }
            return result;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            final int result = super.read(buffer, offset, length);
            if (result > 0) {
                bytesRead.addAndGet(result);
            }
            return result;
        }

        @Override
        public long skip(final long amount) throws IOException {
            final long skipped = super.skip(amount);
            bytesRead.addAndGet(skipped);
            return skipped;
        }
    }
}
