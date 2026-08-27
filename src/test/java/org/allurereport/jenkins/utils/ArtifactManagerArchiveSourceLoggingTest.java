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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactManagerArchiveSourceLoggingTest {

    private static final String LARGE_ENTRY = "allure-report/data/leading.bin";
    private static final String REPORT_ENTRY = "allure-report/index.html";
    private static final String REPORT_CONTENT = "<html>report</html>";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule()
            .record(ArtifactManagerArchiveSource.class, Level.WARNING)
            .capture(100)
            .quiet();

    @Test
    public void sequentialFallbackWarningIsActionableAndDeduplicated() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        final byte[] archive = ZipTestArchive.createWithLargeLeadingEntry(
                LARGE_ENTRY,
                64 * 1024,
                REPORT_ENTRY,
                REPORT_CONTENT
        );

        try (RangeAwareArtifactManager manager = new RangeAwareArtifactManager(archive, false)) {
            manager.install(build);

            assertReportEntry(build);
            assertReportEntry(build);

            assertThat(logger.getRecords())
                    .filteredOn(record -> record.getLevel().equals(Level.WARNING))
                    .filteredOn(record -> record.getMessage().contains("Falling back to sequential ZIP streaming"))
                    .singleElement()
                    .extracting(LogRecord::getMessage)
                    .asString()
                    .contains(build.getExternalizableId())
                    .contains("Artifact storage does not support HTTP byte ranges")
                    .contains("Each report asset request may read the archive from the beginning")
                    .contains("HTTP 206")
                    .contains("once per build and failure reason");
        }
    }

    private void assertReportEntry(final FreeStyleBuild build) throws Exception {
        try (ArtifactManagerArchiveSource source = new ArtifactManagerArchiveSource(build);
             InputStream inputStream = source.openEntry(REPORT_ENTRY)) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(REPORT_CONTENT);
        }
    }
}
