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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.NcssCount"})
public final class AllureSummaryExtractor {

    private static final Logger LOG = Logger.getLogger(AllureSummaryExtractor.class.getName());

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
        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(run)) {
            if (!source.exists()) {
                return null;
            }
            final Optional<String> entryName = findSummaryEntryName(source, reportPath, isAllure3);
            if (entryName.isPresent()) {
                try (InputStream is = source.openEntry(entryName.get())) {
                    if (entryName.get().endsWith(SEPARATOR + FILE_STATISTIC)) {
                        return parseStatisticJson(is);
                    }
                    return parseSummaryJson(is);
                }
            }
        } catch (IOException | InterruptedException ex) {
            LOG.log(Level.FINE, "Unable to read Allure summary from ZIP for {0}: {1}",
                    new Object[]{reportPath, ex.toString()});
        }
        return null;
    }

    private static Optional<String> findSummaryEntryName(final AllureReportArchiveSource source,
                                                         final String reportPath,
                                                         final boolean isAllure3)
            throws IOException, InterruptedException {
        if (isAllure3) {
            return firstPresent(
                    findEntry(source, reportPath, DIR_AWESOME + SEPARATOR + DIR_WIDGETS, FILE_STATISTIC),
                    findEntry(source, reportPath, DIR_WIDGETS, FILE_STATISTIC),
                    findEntry(source, reportPath, DIR_AWESOME + SEPARATOR + DIR_EXPORT, FILE_SUMMARY),
                    findEntry(source, reportPath, DIR_AWESOME + SEPARATOR + DIR_WIDGETS, FILE_SUMMARY),
                    findEntry(source, reportPath, DIR_EXPORT, FILE_SUMMARY),
                    findEntry(source, reportPath, DIR_WIDGETS, FILE_SUMMARY)
            );
        }
        return firstPresent(
                findEntry(source, reportPath, DIR_EXPORT, FILE_SUMMARY),
                findEntry(source, reportPath, DIR_WIDGETS, FILE_SUMMARY)
        );
    }

    private static Optional<String> findEntry(final AllureReportArchiveSource source,
                                              final String reportPath,
                                              final String location,
                                              final String fileName)
            throws IOException, InterruptedException {
        final String prefix = reportPath.concat(SEPARATOR).concat(location);
        final String toSearch = prefix.concat(SEPARATOR).concat(fileName);
        final List<String> entries = source.listEntries(prefix);
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(name -> name.equals(toSearch))
                .findFirst();
    }

    @SafeVarargs
    private static <T> Optional<T> firstPresent(final Optional<T>... options) {
        for (Optional<T> o : options) {
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
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
