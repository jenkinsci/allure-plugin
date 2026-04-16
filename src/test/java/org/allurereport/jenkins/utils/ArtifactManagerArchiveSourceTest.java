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
import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.StreamBuildListener;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArtifactManagerArchiveSourceTest {

    private static final String HISTORY_ENTRY = "allure-report/history/history.json";
    private static final String HISTORY_PREFIX = "allure-report/history";
    private static final String CATEGORIES_ENTRY = "allure-report/history/categories.json";
    private static final String SUMMARY_ENTRY = "allure-report/widgets/summary.json";
    private static final String EMPTY_HISTORY = "{\"items\":[]}";
    private static final String ZIP_HISTORY = "{\"zip\":true}";
    private static final String EMPTY_OBJECT = "{}";
    private static final String EMPTY_ARRAY = "[]";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void openEntryReadsDirectArtifactWithoutZip() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveArtifacts(project, build, mapOf(HISTORY_ENTRY, EMPTY_HISTORY));

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build);
             InputStream inputStream = source.openEntry(HISTORY_ENTRY)) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(EMPTY_HISTORY);
        }
    }

    @Test
    public void openEntryReadsZipArtifactWhenDirectArtifactIsMissing() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveZip(project, build, mapOf(HISTORY_ENTRY, ZIP_HISTORY));

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build);
             InputStream inputStream = source.openEntry(HISTORY_ENTRY)) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(ZIP_HISTORY);
        }
    }

    @Test
    public void listEntriesTraversesDirectArtifactsRecursively() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveArtifacts(project, build, mapOf(
                HISTORY_ENTRY, EMPTY_OBJECT,
                CATEGORIES_ENTRY, EMPTY_ARRAY,
                SUMMARY_ENTRY, EMPTY_OBJECT
        ));

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build)) {
            final List<String> entries = source.listEntries(HISTORY_PREFIX);

            assertThat(entries).containsExactlyInAnyOrder(
                    HISTORY_ENTRY,
                    CATEGORIES_ENTRY
            );
        }
    }

    @Test
    public void listEntriesReadsMatchingEntriesFromZipArtifact() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveZip(project, build, mapOf(
                HISTORY_ENTRY, EMPTY_OBJECT,
                CATEGORIES_ENTRY, EMPTY_ARRAY,
                SUMMARY_ENTRY, EMPTY_OBJECT
        ));

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build)) {
            final List<String> entries = source.listEntries(HISTORY_PREFIX);

            assertThat(entries).containsExactlyInAnyOrder(
                    HISTORY_ENTRY,
                    CATEGORIES_ENTRY
            );
        }
    }

    @Test
    public void existsReturnsTrueOnlyWhenZipArtifactIsArchived() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveZip(project, build, mapOf("allure-report/index.html", "<html/>"));

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build)) {
            assertThat(source.exists()).isTrue();
        }
    }

    @Test
    public void missingArtifactsReturnEmptyResultsAndMissingEntryError() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build)) {
            assertThat(source.exists()).isFalse();
            assertThat(source.listEntries(HISTORY_PREFIX)).isEmpty();
            assertThatThrownBy(() -> source.openEntry(HISTORY_ENTRY))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("allure-report.zip");
        }
    }

    private void archiveArtifacts(final FreeStyleProject project,
                                  final FreeStyleBuild build,
                                  final Map<String, String> artifacts) throws Exception {
        final FilePath workspace = Objects.requireNonNull(jRule.jenkins.getWorkspaceFor(project));
        workspace.deleteRecursive();
        workspace.mkdirs();

        final Map<String, String> archivedPaths = new LinkedHashMap<>();
        for (Map.Entry<String, String> artifact : artifacts.entrySet()) {
            final FilePath target = workspace.child(artifact.getKey());
            final FilePath parent = target.getParent();
            if (parent != null) {
                parent.mkdirs();
            }
            try (OutputStream outputStream = target.write()) {
                outputStream.write(artifact.getValue().getBytes(StandardCharsets.UTF_8));
            }
            archivedPaths.put(artifact.getKey(), artifact.getKey());
        }

        final StreamBuildListener listener = new StreamBuildListener(
                OutputStream.nullOutputStream(), StandardCharsets.UTF_8
        );
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        build.pickArtifactManager().archive(workspace, launcher, listener, archivedPaths);
    }

    private void archiveZip(final FreeStyleProject project,
                            final FreeStyleBuild build,
                            final Map<String, String> zipEntries) throws Exception {
        final FilePath workspace = Objects.requireNonNull(jRule.jenkins.getWorkspaceFor(project));
        workspace.deleteRecursive();
        workspace.mkdirs();

        final FilePath zip = workspace.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        try (OutputStream fileOut = zip.write(); ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (Map.Entry<String, String> entry : zipEntries.entrySet()) {
                zipOut.putNextEntry(new ZipEntry(entry.getKey()));
                zipOut.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }

        final StreamBuildListener listener = new StreamBuildListener(
                OutputStream.nullOutputStream(), StandardCharsets.UTF_8
        );
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        build.pickArtifactManager().archive(
                workspace,
                launcher,
                listener,
                mapOf(
                        AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP,
                        AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP
                )
        );
    }

    private Map<String, String> mapOf(final String... keyValues) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
