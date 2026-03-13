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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FallbackArchiveSourceTest {

    private static final String ENTRY_PATH = "allure-report/index.html";
    private static final String ENTRY_CONTENT = "primary content";
    private static final String FALLBACK_CONTENT = "fallback content";
    private static final String HISTORY_PREFIX = "allure-report/history";
    private static final String HISTORY_ENTRY = "allure-report/history/entry.json";
    private static final String PRIMARY = "primary";
    private static final String FALLBACK = "fallback";
    private static final String ENTRY_JSON_SUFFIX = "/entry.json";

    private static AllureReportArchiveSource existingSource(final String content) {
        return new ExistingSource(content);
    }

    private static AllureReportArchiveSource missingSource() {
        return new MissingSource();
    }

    private static AllureReportArchiveSource sourceThrowingOnClose(final String content) {
        return new ThrowingOnCloseSource(content);
    }

    @Test
    public void activeSourcePrimaryExistsReturnsPrimary() throws IOException, InterruptedException {
        final AllureReportArchiveSource primary = existingSource(ENTRY_CONTENT);
        final AllureReportArchiveSource fallback = existingSource(FALLBACK_CONTENT);
        final FallbackArchiveSource source = new FallbackArchiveSource(primary, fallback);

        final AllureReportArchiveSource active = source.activeSource();

        assertThat(active).isSameAs(primary);
    }

    @Test
    public void activeSourcePrimaryMissingFallbackExistsReturnsFallback()
            throws IOException, InterruptedException {
        final AllureReportArchiveSource primary = missingSource();
        final AllureReportArchiveSource fallback = existingSource(FALLBACK_CONTENT);
        final FallbackArchiveSource source = new FallbackArchiveSource(primary, fallback);

        final AllureReportArchiveSource active = source.activeSource();

        assertThat(active).isSameAs(fallback);
    }

    @Test
    public void activeSourceBothMissingReturnsNull() throws IOException, InterruptedException {
        final AllureReportArchiveSource primary = missingSource();
        final AllureReportArchiveSource fallback = missingSource();
        final FallbackArchiveSource source = new FallbackArchiveSource(primary, fallback);

        final AllureReportArchiveSource active = source.activeSource();

        assertThat(active).isNull();
    }

    @Test
    public void existsPrimaryExistsReturnsTrue() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                existingSource(ENTRY_CONTENT), missingSource());

        assertThat(source.exists()).isTrue();
    }

    @Test
    public void existsOnlyFallbackExistsReturnsTrue() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                missingSource(), existingSource(FALLBACK_CONTENT));

        assertThat(source.exists()).isTrue();
    }

    @Test
    public void existsBothMissingReturnsFalse() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(missingSource(), missingSource());

        assertThat(source.exists()).isFalse();
    }

    @Test
    public void openEntryPrimaryExistsReadsPrimaryContent() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                existingSource(ENTRY_CONTENT), existingSource(FALLBACK_CONTENT));

        try (InputStream is = source.openEntry(ENTRY_PATH)) {
            final String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(ENTRY_CONTENT);
        }
    }

    @Test
    public void openEntryPrimaryMissingReadsFallbackContent() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                missingSource(), existingSource(FALLBACK_CONTENT));

        try (InputStream is = source.openEntry(ENTRY_PATH)) {
            final String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(FALLBACK_CONTENT);
        }
    }

    @Test
    public void openEntryBothMissingThrowsNoSuchElementException() {
        final FallbackArchiveSource source = new FallbackArchiveSource(missingSource(), missingSource());

        assertThatThrownBy(() -> source.openEntry(ENTRY_PATH))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void listEntriesPrimaryExistsReturnsPrimaryEntries() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                existingSource(ENTRY_CONTENT), existingSource(FALLBACK_CONTENT));

        final List<String> entries = source.listEntries(HISTORY_PREFIX);

        assertThat(entries).containsExactly(HISTORY_ENTRY);
    }

    @Test
    public void listEntriesPrimaryMissingReturnsFallbackEntries() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                missingSource(), existingSource(FALLBACK_CONTENT));

        final List<String> entries = source.listEntries(HISTORY_PREFIX);

        assertThat(entries).containsExactly(HISTORY_ENTRY);
    }

    @Test
    public void listEntriesBothMissingReturnsEmptyList() throws IOException, InterruptedException {
        final FallbackArchiveSource source = new FallbackArchiveSource(missingSource(), missingSource());

        final List<String> entries = source.listEntries(HISTORY_PREFIX);

        assertThat(entries).isEmpty();
    }

    @Test
    public void closeClosesBothSources() throws IOException {
        final TrackingSource primary = new TrackingSource(true);
        final TrackingSource fallback = new TrackingSource(false);
        final FallbackArchiveSource source = new FallbackArchiveSource(primary, fallback);

        source.close();

        assertThat(primary.isClosed()).isTrue();
        assertThat(fallback.isClosed()).isTrue();
    }

    @Test
    public void closePrimaryThrowsFallbackStillClosed() {
        final TrackingSource fallback = new TrackingSource(false);
        final FallbackArchiveSource source = new FallbackArchiveSource(
                sourceThrowingOnClose(ENTRY_CONTENT), fallback);

        assertThatThrownBy(source::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("close failed");

        assertThat(fallback.isClosed()).isTrue();
    }

    @Test
    public void closeBothThrowSecondExceptionIsSuppressed() {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                sourceThrowingOnClose(PRIMARY),
                sourceThrowingOnClose(FALLBACK));

        assertThatThrownBy(source::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining(PRIMARY)
                .satisfies(ex -> {
                    assertThat(ex.getSuppressed()).hasSize(1);
                    assertThat(ex.getSuppressed()[0])
                            .isInstanceOf(IOException.class)
                            .hasMessageContaining(FALLBACK);
                });
    }

    @Test
    public void closeNeitherThrowsDoesNotThrow() throws IOException {
        final FallbackArchiveSource source = new FallbackArchiveSource(
                existingSource(ENTRY_CONTENT), existingSource(FALLBACK_CONTENT));

        source.close();
    }

    private static final class ExistingSource implements AllureReportArchiveSource {

        private final String content;
        private boolean closed;

        ExistingSource(final String content) {
            this.content = content;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public InputStream openEntry(final String entryPath) {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public List<String> listEntries(final String prefix) {
            return Collections.singletonList(prefix + ENTRY_JSON_SUFFIX);
        }

        @Override
        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private static final class MissingSource implements AllureReportArchiveSource {

        private boolean closed;

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public InputStream openEntry(final String entryPath) {
            throw new NoSuchElementException("Source does not exist");
        }

        @Override
        public List<String> listEntries(final String prefix) {
            return Collections.emptyList();
        }

        @Override
        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private static final class ThrowingOnCloseSource implements AllureReportArchiveSource {

        private final String content;

        ThrowingOnCloseSource(final String content) {
            this.content = content;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public InputStream openEntry(final String entryPath) {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public List<String> listEntries(final String prefix) {
            return Collections.singletonList(prefix + ENTRY_JSON_SUFFIX);
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed for: " + content);
        }
    }

    private static final class TrackingSource implements AllureReportArchiveSource {

        private final boolean exists;
        private boolean closed;

        TrackingSource(final boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean exists() {
            return exists;
        }

        @Override
        public InputStream openEntry(final String entryPath) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public List<String> listEntries(final String prefix) {
            return Collections.emptyList();
        }

        @Override
        public void close() {
            this.closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }
}
