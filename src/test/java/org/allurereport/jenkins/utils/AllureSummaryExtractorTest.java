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
import jenkins.util.VirtualFile;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureSummaryExtractorTest {

    private static final String SUMMARY_ARTIFACT = "allure-summary.json";
    private static final String REPORT_PATH = "allure-report";
    private static final String SUMMARY_ENTRY = "allure-report/widgets/summary.json";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void extractFromSummaryJsonReadsStatisticPayload() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveArtifacts(project, build, mapOf(
                SUMMARY_ARTIFACT,
                summaryJson(3, 1, 2, 4, 5)
        ));

        final VirtualFile artifact = build.getArtifactManager().root().child(SUMMARY_ARTIFACT);
        final BuildSummary summary = AllureSummaryExtractor.extractFromSummaryJson(artifact);

        assertCounts(summary, 3, 1, 2, 4, 5);
    }

    @Test
    public void extractReadsAllure2SummaryFromZipWhenNoSummaryArtifactExists() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveZip(project, build, mapOf(
                SUMMARY_ENTRY,
                summaryJson(4, 2, 1, 0, 3)
        ));

        final BuildSummary summary = AllureSummaryExtractor.extract(build, REPORT_PATH, false);

        assertCounts(summary, 4, 2, 1, 0, 3);
    }

    @Test
    public void extractReadsAllure3AwesomeStatisticFromZip() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archiveZip(project, build, mapOf(
                "allure-report/awesome/widgets/statistic.json",
                statisticJson(6, 1, 0, 2, 3)
        ));

        final BuildSummary summary = AllureSummaryExtractor.extract(build, REPORT_PATH, true);

        assertCounts(summary, 6, 1, 0, 2, 3);
    }

    @Test
    public void extractFallsBackToUnpackedDirectoryWhenArchiveIsMissing() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        writeFile(
                new FilePath(build.getRootDir()).child(SUMMARY_ENTRY),
                summaryJson(8, 0, 1, 2, 0)
        );

        final BuildSummary summary = AllureSummaryExtractor.extract(build, REPORT_PATH, false);

        assertCounts(summary, 8, 0, 1, 2, 0);
    }

    @Test
    public void extractReturnsEmptySummaryWhenNoSourcesExist() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        final BuildSummary summary = AllureSummaryExtractor.extract(build, REPORT_PATH, false);

        assertCounts(summary, 0, 0, 0, 0, 0);
    }

    private void archiveArtifacts(final FreeStyleProject project,
                                  final FreeStyleBuild build,
                                  final Map<String, String> artifacts) throws Exception {
        final FilePath workspace = Objects.requireNonNull(jRule.jenkins.getWorkspaceFor(project));
        workspace.deleteRecursive();
        workspace.mkdirs();

        final Map<String, String> archivedPaths = new LinkedHashMap<>();
        for (Map.Entry<String, String> artifact : artifacts.entrySet()) {
            writeFile(workspace.child(artifact.getKey()), artifact.getValue());
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
        try (OutputStream outputStream = zip.write(); ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
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

    private void writeFile(final FilePath target, final String content) throws Exception {
        final FilePath parent = target.getParent();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream outputStream = target.write()) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String summaryJson(final int passed,
                               final int failed,
                               final int broken,
                               final int skipped,
                               final int unknown) {
        return String.format(
                "{\"statistic\":{\"passed\":%d,\"failed\":%d,\"broken\":%d,\"skipped\":%d,\"unknown\":%d}}",
                passed, failed, broken, skipped, unknown
        );
    }

    private String statisticJson(final int passed,
                                 final int failed,
                                 final int broken,
                                 final int skipped,
                                 final int unknown) {
        return String.format(
                "{\"passed\":%d,\"failed\":%d,\"broken\":%d,\"skipped\":%d,\"unknown\":%d}",
                passed, failed, broken, skipped, unknown
        );
    }

    private void assertCounts(final BuildSummary summary,
                              final long passed,
                              final long failed,
                              final long broken,
                              final long skipped,
                              final long unknown) {
        assertThat(summary.getPassedCount()).isEqualTo(passed);
        assertThat(summary.getFailedCount()).isEqualTo(failed);
        assertThat(summary.getBrokenCount()).isEqualTo(broken);
        assertThat(summary.getSkipCount()).isEqualTo(skipped);
        assertThat(summary.getUnknownCount()).isEqualTo(unknown);
    }

    private Map<String, String> mapOf(final String... keyValues) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
