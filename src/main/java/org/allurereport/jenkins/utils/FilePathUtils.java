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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FilePathUtils {

    private static final Logger LOG = Logger.getLogger(FilePathUtils.class.getName());

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_REPORT_ZIP = "allure-report.zip";
    private static final String HISTORY_HISTORY_JSON = "/history/history.json";
    private static final String SUMMARY_ARTIFACT_NAME = "allure-summary.json";

    private FilePathUtils() {
    }

    @SuppressWarnings("TrailingComment")
    public static void copyRecursiveTo(final FilePath from,
        final FilePath to,
        final AbstractBuild build,
        final PrintStream logger) throws IOException, InterruptedException { //NOSONAR
        if (from.isRemote() && to.isRemote()) {
            final FilePath tmpMasterFilePath = new FilePath(build.getRootDir()).createTempDir(ALLURE_PREFIX, null);
            from.copyRecursiveTo(tmpMasterFilePath);
            tmpMasterFilePath.copyRecursiveTo(to);
            deleteRecursive(tmpMasterFilePath, logger);
        } else {
            from.copyRecursiveTo(to);
        }
    }

    @SuppressWarnings("TrailingComment")
    public static void deleteRecursive(final FilePath filePath,
        final PrintStream logger) {
        try {
            filePath.deleteContents();
            filePath.deleteRecursive();
        } catch (IOException | InterruptedException e) { //NOSONAR
            logger.printf("Can't delete directory [%s]%n", filePath);
        }
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static FilePath getPreviousReportWithHistory(final Run<?, ?> run,
        final String reportPath)
        throws IOException, InterruptedException {
        Run<?, ?> current = run;
        while (current != null) {
            final FilePath previousReport = new FilePath(current.getArtifactsDir()).child(ALLURE_REPORT_ZIP);
            if (previousReport.exists() && isHistoryNotEmpty(previousReport.toVirtualFile(), reportPath)) {
                return previousReport;
            }
            current = current.getPreviousCompletedBuild();
        }
        return null;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public static VirtualFile getPreviousReportZipWithHistory(final Run<?, ?> run,
                                                              final String reportPath)
            throws IOException, InterruptedException {
        Run<?, ?> current = run;
        while (current != null) {
            final VirtualFile root = current.getArtifactManager().root();
            final VirtualFile zip = root.child(ALLURE_REPORT_ZIP);
            if (zip.exists() && isHistoryNotEmpty(zip, reportPath)) {
                return zip;
            }
            current = current.getPreviousCompletedBuild();
        }
        return null;
    }

    private static boolean isHistoryNotEmpty(final VirtualFile previousReportZip,
                                             final String reportPath) throws IOException {
        final String expected = reportPath + HISTORY_HISTORY_JSON;
        final ObjectMapper mapper = new ObjectMapper();

        try (InputStream in = previousReportZip.open();
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && expected.equals(entry.getName())) {
                    final JsonNode historyJson = mapper.readTree(zis);
                    return historyJson != null && historyJson.elements().hasNext();
                }
                entry = zis.getNextEntry();
            }
        }

        return false;
    }

    /**
     * Extract build summary from the Allure report.
     *
     * @param run the build run
     * @param reportPath the path to the report
     * @return the build summary
     */
    public static BuildSummary extractSummary(final Run<?, ?> run, final String reportPath) {
        return extractSummary(run, reportPath, false);
    }

    /**
     * Extract build summary from the Allure report.
     *
     * @param run the build run
     * @param reportPath the path to the report
     * @param isAllure3 whether this is an Allure 3 report
     * @return the build summary
     */
    public static BuildSummary extractSummary(final Run<?, ?> run, final String reportPath, final boolean isAllure3) {
        try {
            final VirtualFile summary = run.getArtifactManager().root().child(SUMMARY_ARTIFACT_NAME);
            if (summary.exists()) {
                return AllureSummaryExtractor.extractFromSummaryJson(summary);
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to extract summary from artifact", e);
        }
        return AllureSummaryExtractor.extract(run, reportPath, isAllure3);
    }
}
