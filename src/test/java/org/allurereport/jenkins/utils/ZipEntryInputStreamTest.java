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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ZipEntryInputStreamTest {

    private static final String ENTRY_CONTENT = "hello world";
    private static final String ENTRY_PATH = "allure-report/index.html";
    private static final String HISTORY_ENTRY = "allure-report/history/history.json";
    private static final String HISTORY_ENTRY_2 = "allure-report/history/categories.json";
    private static final String HISTORY_PREFIX = "allure-report/history";
    private static final String MISSING_ENTRY = "missing/entry.html";
    private static final String SUMMARY_JSON = "allure-report/widgets/summary.json";
    private static final String EMPTY_JSON = "{}";
    private static final String HISTORY_CONTENT = "history content";
    private static final String ROOT_SLASH = "/";

    private static byte[] createZipInMemory(final String... nameContentPairs) throws IOException {
        final ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(zipBuffer)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                final String entryName = nameContentPairs[i];
                final String entryContent = nameContentPairs[i + 1];
                zipOutput.putNextEntry(new ZipEntry(entryName));
                if (entryContent != null) {
                    zipOutput.write(entryContent.getBytes(StandardCharsets.UTF_8));
                }
                zipOutput.closeEntry();
            }
        }
        return zipBuffer.toByteArray();
    }

    private static byte[] createZipWithDirectoryInMemory(final String dirName,
                                                          final String fileName,
                                                          final String fileContent) throws IOException {
        final ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(zipBuffer)) {
            zipOutput.putNextEntry(new ZipEntry(dirName + ROOT_SLASH));
            zipOutput.closeEntry();
            zipOutput.putNextEntry(new ZipEntry(dirName + ROOT_SLASH + fileName));
            zipOutput.write(fileContent.getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }
        return zipBuffer.toByteArray();
    }

    @Test
    public void openExistingEntryReturnsCorrectContent() throws IOException {
        final byte[] zipBytes = createZipInMemory(ENTRY_PATH, ENTRY_CONTENT);

        try (InputStream entryStream = ZipEntryInputStream.open(new ByteArrayInputStream(zipBytes), ENTRY_PATH)) {
            final String content = new String(entryStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(ENTRY_CONTENT);
        }
    }

    @Test
    public void openMissingEntryThrowsNoSuchElementException() throws IOException {
        final byte[] zipBytes = createZipInMemory(ENTRY_PATH, ENTRY_CONTENT);

        assertThatThrownBy(
                () -> ZipEntryInputStream.open(new ByteArrayInputStream(zipBytes), MISSING_ENTRY))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(MISSING_ENTRY);
    }

    @Test
    public void openEmptyZipThrowsNoSuchElementException() throws IOException {
        final byte[] zipBytes = createZipInMemory();

        assertThatThrownBy(() -> ZipEntryInputStream.open(new ByteArrayInputStream(zipBytes), ENTRY_PATH))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void openMultipleEntriesReturnsCorrectOne() throws IOException {
        final byte[] zipBytes = createZipInMemory(
                ENTRY_PATH, "index content",
                HISTORY_ENTRY, HISTORY_CONTENT,
                SUMMARY_JSON, "summary content"
        );

        try (InputStream entryStream = ZipEntryInputStream.open(new ByteArrayInputStream(zipBytes), HISTORY_ENTRY)) {
            final String content = new String(entryStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo(HISTORY_CONTENT);
        }
    }

    @Test
    public void openClosingReturnedStreamDoesNotThrow() throws IOException {
        final byte[] zipBytes = createZipInMemory(ENTRY_PATH, ENTRY_CONTENT);
        final InputStream entryStream = ZipEntryInputStream.open(new ByteArrayInputStream(zipBytes), ENTRY_PATH);
        entryStream.close();
    }

    @Test
    public void listEntriesWithMatchingPrefixReturnsMatchingFiles() throws IOException {
        final byte[] zipBytes = createZipInMemory(
                HISTORY_ENTRY, EMPTY_JSON,
                HISTORY_ENTRY_2, "[]",
                SUMMARY_JSON, EMPTY_JSON
        );

        final List<String> entries = ZipEntryInputStream.listEntries(
                new ByteArrayInputStream(zipBytes),
                HISTORY_PREFIX
        );

        assertThat(entries)
                .hasSize(2)
                .contains(HISTORY_ENTRY, HISTORY_ENTRY_2);
    }

    @Test
    public void listEntriesNoMatchingPrefixReturnsEmptyList() throws IOException {
        final byte[] zipBytes = createZipInMemory(ENTRY_PATH, ENTRY_CONTENT);

        final List<String> entries = ZipEntryInputStream.listEntries(
                new ByteArrayInputStream(zipBytes),
                "nonexistent/"
        );

        assertThat(entries).isEmpty();
    }

    @Test
    public void listEntriesDirectoryEntriesExcluded() throws IOException {
        final byte[] zipBytes = createZipWithDirectoryInMemory(HISTORY_PREFIX, "history.json", EMPTY_JSON);

        final List<String> entries = ZipEntryInputStream.listEntries(
                new ByteArrayInputStream(zipBytes),
                HISTORY_PREFIX
        );

        assertThat(entries).allMatch(entryName -> !entryName.endsWith(ROOT_SLASH));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).isEqualTo(HISTORY_ENTRY);
    }

    @Test
    public void listEntriesEmptyZipReturnsEmptyList() throws IOException {
        final byte[] zipBytes = createZipInMemory();

        final List<String> entries = ZipEntryInputStream.listEntries(
                new ByteArrayInputStream(zipBytes),
                HISTORY_PREFIX
        );

        assertThat(entries).isEmpty();
    }

    @Test
    public void listEntriesExactPrefixMatchIncludesEntry() throws IOException {
        final byte[] zipBytes = createZipInMemory(
                HISTORY_ENTRY, EMPTY_JSON,
                "allure-report/history/history.json.bak", "backup"
        );

        final List<String> entries = ZipEntryInputStream.listEntries(
                new ByteArrayInputStream(zipBytes),
                HISTORY_ENTRY
        );

        assertThat(entries).hasSize(2);
    }
}
