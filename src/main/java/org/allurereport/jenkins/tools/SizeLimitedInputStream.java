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
package org.allurereport.jenkins.tools;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Rejects streams that exceed a byte budget or total elapsed-time budget.
 */
final class SizeLimitedInputStream extends FilterInputStream {

    private final long maxBytes;
    private final long startedNanos;
    private final long maxDurationNanos;
    private final String description;
    private long bytesRead;

    SizeLimitedInputStream(final InputStream input,
                           final long maxBytes,
                           final long maxDurationMillis,
                           final String description) {
        super(input);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        if (maxDurationMillis <= 0) {
            throw new IllegalArgumentException("maxDurationMillis must be positive");
        }
        this.maxBytes = maxBytes;
        this.startedNanos = System.nanoTime();
        this.maxDurationNanos = TimeUnit.MILLISECONDS.toNanos(maxDurationMillis);
        this.description = description;
    }

    @Override
    public int read() throws IOException {
        checkDuration();
        if (bytesRead == maxBytes) {
            return readBeyondLimit();
        }
        final int value = in.read();
        checkDuration();
        if (value >= 0) {
            bytesRead++;
        }
        return value;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        checkDuration();
        if (bytesRead == maxBytes) {
            return readBeyondLimit();
        }
        final int permitted = (int) Math.min(length, maxBytes - bytesRead);
        final int count = in.read(buffer, offset, permitted);
        checkDuration();
        if (count > 0) {
            bytesRead += count;
        }
        return count;
    }

    long getBytesRead() {
        return bytesRead;
    }

    void checkTimeLimit() throws IOException {
        checkDuration();
    }

    private int readBeyondLimit() throws IOException {
        final int value = in.read();
        checkDuration();
        if (value < 0) {
            return -1;
        }
        throw new IOException(description + " exceeds the limit of " + maxBytes + " bytes");
    }

    private void checkDuration() throws IOException {
        if (System.nanoTime() - startedNanos > maxDurationNanos) {
            throw new IOException(description + " exceeded the total time limit");
        }
    }
}
