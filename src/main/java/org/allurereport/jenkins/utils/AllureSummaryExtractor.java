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
import hudson.model.Run;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.NcssCount"})
public final class AllureSummaryExtractor {

    private static final Logger LOG = Logger.getLogger(AllureSummaryExtractor.class.getName());
    private static final String ALLURE_REPORT_ZIP = "allure-report.zip";

    private static final String KEY_PASSED = "passed";
    private static final String KEY_FAILED = "failed";
    private static final String KEY_BROKEN = "broken";
    private static final String KEY_SKIPPED = "skipped";
    private static final String KEY_UNKNOWN = "unknown";

    private static final String DIR_EXPORT = "export";
    private static final String DIR_WIDGETS = "widgets";
    private static final String DIR_AWESOME = "awesome";
    private static final String FILE_SUMMARY = "summary.json";
    private static final String FILE_STATISTIC = "statistic.json";
    private static final String KEY_STATISTIC = "statistic";

    private static final String SEPARATOR = "/";

    private AllureSummaryExtractor() {
    }

    public static BuildSummary extractFromSummaryJson(final VirtualFile summaryArtifact) throws IOException {
        try (InputStream is = summaryArtifact.open()) {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(is);
            final JsonNode statistic = root != null ? root.get(KEY_STATISTIC) : null;
            if (statistic == null || statistic.isNull()) {
                return emptySummary();
            }
            final Map<String, Integer> statistics = new HashMap<>(5);
            statistics.put(KEY_PASSED, readInt(statistic, KEY_PASSED));
            statistics.put(KEY_FAILED, readInt(statistic, KEY_FAILED));
            statistics.put(KEY_BROKEN, readInt(statistic, KEY_BROKEN));
            statistics.put(KEY_SKIPPED, readInt(statistic, KEY_SKIPPED));
            statistics.put(KEY_UNKNOWN, readInt(statistic, KEY_UNKNOWN));
            return new BuildSummary().withStatistics(statistics);
        }
    }

    private static int readInt(final JsonNode node, final String field) {
        final JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asInt(0) : 0;
    }

    public static BuildSummary extract(final Run<?, ?> run, final String reportPath, final boolean isAllure3) {
        BuildSummary summary = extractFromZip(run, reportPath, isAllure3);
        if (summary != null) {
            return summary;
        }

        summary = extractFromDirectory(run, reportPath, isAllure3);
        if (summary != null) {
            return summary;
        }

        return emptySummary();
    }

    private static BuildSummary emptySummary() {
        final Map<String, Integer> statistics = new HashMap<>(5);
        statistics.put(KEY_PASSED, 0);
        statistics.put(KEY_FAILED, 0);
        statistics.put(KEY_BROKEN, 0);
        statistics.put(KEY_SKIPPED, 0);
        statistics.put(KEY_UNKNOWN, 0);
        return new BuildSummary().withStatistics(statistics);
    }

    private static BuildSummary extractFromZip(final Run<?, ?> run, final String reportPath, final boolean isAllure3) {
        try {
            final VirtualFile reportZip = run.getArtifactManager().root().child(ALLURE_REPORT_ZIP);
            if (!reportZip.exists()) {
                return null;
            }
            return extractFromZipStream(reportZip, reportPath, isAllure3);
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Unable to read Allure summary from ZIP for {0}: {1}",
                    new Object[]{reportPath, ex.toString()});
        }
        return null;
    }

    private static BuildSummary extractFromZipStream(final VirtualFile reportZip,
                                                     final String reportPath,
                                                     final boolean isAllure3) throws IOException {
        try (InputStream in = reportZip.open();
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && isSummaryEntry(entry.getName(), reportPath, isAllure3)) {
                    final String name = entry.getName();
                    if (name.endsWith(SEPARATOR + FILE_STATISTIC)) {
                        return parseStatisticJson(zis);
                    }
                    return parseSummaryJson(zis);
                }
                entry = zis.getNextEntry();
            }
        }
        return null;
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private static boolean isSummaryEntry(final String entryName,
                                         final String reportPath,
                                         final boolean isAllure3) {
        if (isAllure3) {
            return isAllure3SummaryEntry(entryName, reportPath);
        }
        return isAllure2SummaryEntry(entryName, reportPath);
    }

    @SuppressWarnings("checkstyle:BooleanExpressionComplexity")
    private static boolean isAllure3SummaryEntry(final String entryName, final String reportPath) {
        final String awesomeWidgetsStat = reportPath + SEPARATOR + DIR_AWESOME
                + SEPARATOR + DIR_WIDGETS + SEPARATOR + FILE_STATISTIC;
        final String widgetsStat = reportPath + SEPARATOR + DIR_WIDGETS + SEPARATOR + FILE_STATISTIC;
        final String awesomeExport = reportPath + SEPARATOR + DIR_AWESOME
                + SEPARATOR + DIR_EXPORT + SEPARATOR + FILE_SUMMARY;
        final String awesomeWidgets = reportPath + SEPARATOR + DIR_AWESOME
                + SEPARATOR + DIR_WIDGETS + SEPARATOR + FILE_SUMMARY;
        final String export = reportPath + SEPARATOR + DIR_EXPORT + SEPARATOR + FILE_SUMMARY;
        final String widgets = reportPath + SEPARATOR + DIR_WIDGETS + SEPARATOR + FILE_SUMMARY;

        return awesomeWidgetsStat.equals(entryName)
                || widgetsStat.equals(entryName)
                || awesomeExport.equals(entryName)
                || awesomeWidgets.equals(entryName)
                || export.equals(entryName)
                || widgets.equals(entryName);
    }

    private static boolean isAllure2SummaryEntry(final String entryName, final String reportPath) {
        final String export = reportPath + SEPARATOR + DIR_EXPORT + SEPARATOR + FILE_SUMMARY;
        final String widgets = reportPath + SEPARATOR + DIR_WIDGETS + SEPARATOR + FILE_SUMMARY;
        return export.equals(entryName) || widgets.equals(entryName);
    }

    private static BuildSummary extractFromDirectory(
            final Run<?, ?> run, final String reportPath, final boolean isAllure3
    ) {
        try {
            final FilePath reportDir = new FilePath(run.getRootDir()).child(reportPath);
            final FilePath json = findSummaryFileInDirectory(reportDir, isAllure3);
            if (json.exists()) {
                try (InputStream is = json.read()) {
                    final String remote = json.getRemote().replace('\\', '/');
                    if (remote.endsWith(SEPARATOR + FILE_STATISTIC)) {
                        return parseStatisticJson(is);
                    }
                    return parseSummaryJson(is);
                }
            }
        } catch (IOException | InterruptedException ex) {
            LOG.log(Level.FINE, "Unable to read Allure summary from unpacked dir for {0}: {1}",
                    new Object[]{reportPath, ex.toString()});
        }
        return null;
    }

    private static FilePath findSummaryFileInDirectory(final FilePath reportDir, final boolean isAllure3)
            throws IOException, InterruptedException {
        if (isAllure3) {
            final FilePath allure3 = findAllure3SummaryFile(reportDir);
            if (allure3 != null) {
                return allure3;
            }
        }
        return findAllure2SummaryFile(reportDir);
    }

    private static FilePath findAllure3SummaryFile(final FilePath reportDir) throws IOException, InterruptedException {
        final FilePath awesomeDir = reportDir.child(DIR_AWESOME);
        if (awesomeDir.exists()) {
            final FilePath awesomeStat = awesomeDir.child(DIR_WIDGETS).child(FILE_STATISTIC);
            if (awesomeStat.exists()) {
                return awesomeStat;
            }
            final FilePath awesomeExportJson = awesomeDir.child(DIR_EXPORT).child(FILE_SUMMARY);
            if (awesomeExportJson.exists()) {
                return awesomeExportJson;
            }
            final FilePath awesomeWidgetsJson = awesomeDir.child(DIR_WIDGETS).child(FILE_SUMMARY);
            if (awesomeWidgetsJson.exists()) {
                return awesomeWidgetsJson;
            }
        }
        final FilePath stat = reportDir.child(DIR_WIDGETS).child(FILE_STATISTIC);
        return stat.exists() ? stat : null;
    }

    private static FilePath findAllure2SummaryFile(final FilePath reportDir) throws IOException, InterruptedException {
        final FilePath exportJson = reportDir.child(DIR_EXPORT).child(FILE_SUMMARY);
        if (exportJson.exists()) {
            return exportJson;
        }
        return reportDir.child(DIR_WIDGETS).child(FILE_SUMMARY);
    }

    private static BuildSummary parseSummaryJson(final InputStream inputStream) throws IOException {
        return getBuildSummary(inputStream);
    }

    private static BuildSummary getBuildSummary(final InputStream inputStream) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(inputStream);
        final JsonNode statisticNode = root.hasNonNull(KEY_STATISTIC) ? root.get(KEY_STATISTIC) : root;

        final Map<String, Integer> statistics = new HashMap<>(5);
        statistics.put(KEY_PASSED, nodeAsInt(statisticNode.get(KEY_PASSED)));
        statistics.put(KEY_FAILED, nodeAsInt(statisticNode.get(KEY_FAILED)));
        statistics.put(KEY_BROKEN, nodeAsInt(statisticNode.get(KEY_BROKEN)));
        statistics.put(KEY_SKIPPED, nodeAsInt(statisticNode.get(KEY_SKIPPED)));
        statistics.put(KEY_UNKNOWN, nodeAsInt(statisticNode.get(KEY_UNKNOWN)));
        return new BuildSummary().withStatistics(statistics);
    }

    private static BuildSummary parseStatisticJson(final InputStream inputStream) throws IOException {
        return getBuildSummary(inputStream);
    }

    private static int nodeAsInt(final JsonNode node) {
        return (node == null || node.isNull()) ? 0 : node.asInt(0);
    }
}
