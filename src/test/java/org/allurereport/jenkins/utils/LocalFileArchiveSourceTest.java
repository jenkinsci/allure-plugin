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

import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LocalFileArchiveSourceTest {

    private static final String INDEX_HTML = "allure-report/index.html";
    private static final String HISTORY_JSON = "allure-report/history/history.json";
    private static final String CATEGORIES_JSON = "allure-report/history/categories.json";
    private static final String SUMMARY_JSON = "allure-report/widgets/summary.json";
    private static final String INDEX_CONTENT = "<html>report</html>";
    private static final String HISTORY_CONTENT = "{\"items\":[]}";
    private static final String SUMMARY_CONTENT = "{\"statistic\":{}}";
    private static final String MISSING_FILE = "missing/file.html";
    private static final String HISTORY_PREFIX = "allure-report/history";
    private static final String SLASH = "/";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File createZipFile(final String... nameContentPairs) throws IOException {
        final File zipFile = folder.newFile("allure-report.zip");
        try (ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(zipFile))) {
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
        return zipFile;
    }

    private File createZipWithDirectory(final String dirName,
                                        final String fileName,
                                        final String fileContent) throws IOException {
        final File zipFile = folder.newFile("allure-report-dir.zip");
        try (ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipOutput.putNextEntry(new ZipEntry(dirName + SLASH));
            zipOutput.closeEntry();
            zipOutput.putNextEntry(new ZipEntry(dirName + SLASH + fileName));
            zipOutput.write(fileContent.getBytes(StandardCharsets.UTF_8));
            zipOutput.closeEntry();
        }
        return zipFile;
    }

    @Test
    public void existsWhenFilePresentReturnsTrue() throws IOException, InterruptedException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        final LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile));

        assertThat(source.exists()).isTrue();
    }

    @Test
    public void existsWhenFileMissingReturnsFalse() throws IOException, InterruptedException {
        final File missingFile = new File(folder.getRoot(), "nonexistent.zip");
        final LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(missingFile));

        assertThat(source.exists()).isFalse();
    }

    @Test
    public void openEntryExistingEntryReturnsCorrectContent() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            try (InputStream entryStream = source.openEntry(INDEX_HTML)) {
                final String content = new String(entryStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content).isEqualTo(INDEX_CONTENT);
            }
        }
    }

    @Test
    public void openEntryMissingEntryThrowsNoSuchElementException() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            assertThatThrownBy(() -> source.openEntry(MISSING_FILE))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(MISSING_FILE);
        }
    }

    @Test
    public void openEntryMultipleEntriesReturnsCorrectOne() throws IOException {
        final File zipFile = createZipFile(
                INDEX_HTML, INDEX_CONTENT,
                HISTORY_JSON, HISTORY_CONTENT,
                SUMMARY_JSON, SUMMARY_CONTENT
        );
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            try (InputStream entryStream = source.openEntry(HISTORY_JSON)) {
                final String content = new String(entryStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content).isEqualTo(HISTORY_CONTENT);
            }
        }
    }

    @Test
    public void listEntriesWithPrefixReturnsMatchingFiles() throws IOException {
        final File zipFile = createZipFile(
                INDEX_HTML, INDEX_CONTENT,
                HISTORY_JSON, HISTORY_CONTENT,
                CATEGORIES_JSON, "[]",
                SUMMARY_JSON, SUMMARY_CONTENT
        );
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            final List<String> entries = source.listEntries(HISTORY_PREFIX);

            assertThat(entries)
                    .hasSize(2)
                    .contains(HISTORY_JSON, CATEGORIES_JSON);
        }
    }

    @Test
    public void listEntriesNoMatchingPrefixReturnsEmptyList() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            final List<String> entries = source.listEntries("nonexistent/prefix");

            assertThat(entries).isEmpty();
        }
    }

    @Test
    public void listEntriesDirectoryEntriesExcluded() throws IOException {
        final File zipFile = createZipWithDirectory(HISTORY_PREFIX, "history.json", HISTORY_CONTENT);
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            final List<String> entries = source.listEntries(HISTORY_PREFIX);

            assertThat(entries).allMatch(entryName -> !entryName.endsWith(SLASH));
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0)).isEqualTo(HISTORY_JSON);
        }
    }

    @Test
    public void closeWhenNotOpenedDoesNotThrow() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        final LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile));
        source.close();
    }

    @Test
    public void closeAfterOpenEntryClosesZipFile() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        final LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile));
        try (InputStream ignored = source.openEntry(INDEX_HTML)) {
            assertThat(ignored).isNotNull();
        }
        source.close();
        try (InputStream entryStream = source.openEntry(INDEX_HTML)) {
            assertThat(entryStream).isNotNull();
        }
        source.close();
    }

    @Test
    public void tryWithResourcesClosesSourceOnExit() throws IOException, InterruptedException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        try (LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile))) {
            assertThat(source.exists()).isTrue();
        }
    }

    @Test
    public void openEntryAfterCloseReopensZipFile() throws IOException {
        final File zipFile = createZipFile(INDEX_HTML, INDEX_CONTENT);
        final LocalFileArchiveSource source = new LocalFileArchiveSource(new FilePath(zipFile));

        try (InputStream entryStream = source.openEntry(INDEX_HTML)) {
            assertThat(new String(entryStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(INDEX_CONTENT);
        }
        source.close();

        try (InputStream entryStream = source.openEntry(INDEX_HTML)) {
            assertThat(new String(entryStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(INDEX_CONTENT);
        }
        source.close();
    }
}
