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

import hudson.ProxyConfiguration;
import jenkins.util.VirtualFile;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HttpRangeReader {

    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final String PREMATURE_EOF = "Remote ZIP range ended before all bytes were read";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String HEADER_CONTENT_RANGE = "Content-Range";
    private static final String HEADER_RANGE = "Range";
    private static final Pattern CONTENT_RANGE = Pattern.compile(
            "bytes (\\d+)-(\\d+)/(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private final VirtualFile file;
    private final long size;

    HttpRangeReader(final VirtualFile file, final long size) {
        this.file = file;
        this.size = size;
    }

    byte[] readRange(final long start, final int length) throws IOException {
        final byte[] result = new byte[length];
        int offset = 0;
        try (InputStream input = openRange(start, length)) {
            while (offset < result.length) {
                final int read = input.read(result, offset, result.length - offset);
                if (read < 0) {
                    throw new EOFException(PREMATURE_EOF);
                }
                offset += read;
            }
        }
        return result;
    }

    InputStream openRange(final long start, final long length) throws IOException {
        if (length == 0) {
            return InputStream.nullInputStream();
        }
        if (start < 0 || length < 0 || start > size || length > size - start) {
            throw new RemoteZipAccessException("Requested range is outside the remote ZIP");
        }

        final URL url = file.toExternalURL();
        if (url == null) {
            throw new RemoteZipAccessException("Artifact manager does not expose an external ZIP URL");
        }

        final URLConnection rawConnection = ProxyConfiguration.open(url);
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new RemoteZipAccessException("External ZIP URL does not use HTTP");
        }

        final long end = start + length - 1;
        final HttpURLConnection connection = (HttpURLConnection) rawConnection;
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(HEADER_ACCEPT_ENCODING, "identity");
            connection.setRequestProperty(HEADER_RANGE, "bytes=" + start + "-" + end);

            final int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_PARTIAL) {
                closeResponse(connection, status);
                throw new RemoteZipAccessException("Artifact storage does not support HTTP byte ranges");
            }
            validateContentRange(connection.getHeaderField(HEADER_CONTENT_RANGE), start, end);
            return new RangeResponseInputStream(connection.getInputStream(), connection, length);
        } catch (IOException | RuntimeException exception) {
            connection.disconnect();
            throw exception;
        }
    }

    private void validateContentRange(final String header,
                                      final long expectedStart,
                                      final long expectedEnd) throws RemoteZipAccessException {
        final Matcher matcher = header == null ? null : CONTENT_RANGE.matcher(header);
        if (matcher == null || !matcher.matches()) {
            throw new RemoteZipAccessException("Artifact storage returned an invalid Content-Range header");
        }
        try {
            final long actualStart = Long.parseLong(matcher.group(1));
            final long actualEnd = Long.parseLong(matcher.group(2));
            final long actualSize = Long.parseLong(matcher.group(3));
            if (actualStart != expectedStart || actualEnd != expectedEnd || actualSize != size) {
                throw new RemoteZipAccessException("Artifact storage returned an unexpected byte range");
            }
        } catch (NumberFormatException exception) {
            throw new RemoteZipAccessException("Artifact storage returned an invalid byte range", exception);
        }
    }

    private static void closeResponse(final HttpURLConnection connection,
                                      final int status) throws IOException {
        final InputStream response = status >= HttpURLConnection.HTTP_BAD_REQUEST
                ? connection.getErrorStream() : connection.getInputStream();
        if (response != null) {
            response.close();
        }
    }

    private static final class RangeResponseInputStream extends FilterInputStream {

        private final HttpURLConnection connection;
        private long remaining;

        RangeResponseInputStream(final InputStream input,
                                 final HttpURLConnection connection,
                                 final long remaining) {
            super(input);
            this.connection = connection;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            final int result = super.read();
            if (result < 0) {
                throw new EOFException(PREMATURE_EOF);
            }
            remaining--;
            return result;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            final int allowed = (int) Math.min(length, remaining);
            final int result = super.read(buffer, offset, allowed);
            if (result < 0) {
                throw new EOFException(PREMATURE_EOF);
            }
            remaining -= result;
            return result;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
