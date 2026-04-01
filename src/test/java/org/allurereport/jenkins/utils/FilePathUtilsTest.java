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
import hudson.model.Run;
import hudson.model.StreamBuildListener;
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

public class FilePathUtilsTest {

    private static final String REPORT_PATH = "allure-report";
    private static final String HISTORY_ENTRY = "allure-report/history/history.json";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Test
    public void extractSummaryPrefersStandaloneSummaryArtifactOverZip() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        archive(project, build, mapOf(
                "allure-summary.json",
                "{\"statistic\":{\"passed\":7,\"failed\":0,\"broken\":0,\"skipped\":1,\"unknown\":2}}"
        ), mapOf(
                "allure-report/widgets/summary.json",
                "{\"statistic\":{\"passed\":1,\"failed\":2,\"broken\":3,\"skipped\":4,\"unknown\":5}}"
        ));

        final BuildSummary summary = FilePathUtils.extractSummary(build, REPORT_PATH, false);

        assertThat(summary.getPassedCount()).isEqualTo(7);
        assertThat(summary.getFailedCount()).isZero();
        assertThat(summary.getSkipCount()).isEqualTo(1);
        assertThat(summary.getUnknownCount()).isEqualTo(2);
    }

    @Test
    public void extractSummaryReturnsEmptyWhenNoArtifactsOrDirectoriesExist() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        final BuildSummary summary = FilePathUtils.extractSummary(build, REPORT_PATH, false);

        assertThat(summary.getPassedCount()).isZero();
        assertThat(summary.getFailedCount()).isZero();
        assertThat(summary.getBrokenCount()).isZero();
        assertThat(summary.getSkipCount()).isZero();
        assertThat(summary.getUnknownCount()).isZero();
    }

    @Test
    public void getPreviousRunWithHistorySkipsEmptyHistoryAndFindsNearestNonEmptyRun() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();

        final FreeStyleBuild first = jRule.buildAndAssertSuccess(project);
        archive(project, first, mapOf(), mapOf(
                HISTORY_ENTRY,
                "{\"case\":[{}]}"
        ));

        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);
        archive(project, second, mapOf(), mapOf(
                HISTORY_ENTRY,
                "{}"
        ));

        final FreeStyleBuild third = jRule.buildAndAssertSuccess(project);

        final Run<?, ?> previous = FilePathUtils.getPreviousRunWithHistory(third, REPORT_PATH);

        assertThat(previous).isEqualTo(first);
    }

    @Test
    public void getPreviousRunWithHistoryReturnsNullWhenNoPreviousHistoryExists() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild first = jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);

        final Run<?, ?> previous = FilePathUtils.getPreviousRunWithHistory(second, REPORT_PATH);

        assertThat(previous).isNull();
    }

    private void archive(final FreeStyleProject project,
                         final FreeStyleBuild build,
                         final Map<String, String> artifacts,
                         final Map<String, String> zipEntries) throws Exception {
        final FilePath workspace = Objects.requireNonNull(jRule.jenkins.getWorkspaceFor(project));
        workspace.deleteRecursive();
        workspace.mkdirs();

        final Map<String, String> archivedPaths = new LinkedHashMap<>();
        for (Map.Entry<String, String> artifact : artifacts.entrySet()) {
            writeFile(workspace.child(artifact.getKey()), artifact.getValue());
            archivedPaths.put(artifact.getKey(), artifact.getKey());
        }

        if (!zipEntries.isEmpty()) {
            final FilePath zip = workspace.child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
            try (OutputStream outputStream = zip.write(); ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                for (Map.Entry<String, String> entry : zipEntries.entrySet()) {
                    zipOut.putNextEntry(new ZipEntry(entry.getKey()));
                    zipOut.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zipOut.closeEntry();
                }
            }
            archivedPaths.put(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP,
                    AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        }

        final StreamBuildListener listener = new StreamBuildListener(
                OutputStream.nullOutputStream(), StandardCharsets.UTF_8
        );
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        build.pickArtifactManager().archive(workspace, launcher, listener, archivedPaths);
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

    private Map<String, String> mapOf(final String... keyValues) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
