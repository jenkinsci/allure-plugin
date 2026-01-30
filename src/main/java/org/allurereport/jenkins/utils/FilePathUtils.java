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
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.allurereport.jenkins.utils.ZipUtils.listEntries;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
@SuppressWarnings("PMD.GodClass")
public final class FilePathUtils {

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_REPORT_ZIP = "allure-report.zip";
    private static final Logger LOG = Logger.getLogger(FilePathUtils.class.getName());

    private static final String KEY_STATUS = "status";
    private static final String KEY_PASSED = "passed";
    private static final String KEY_FAILED = "failed";
    private static final String KEY_BROKEN = "broken";
    private static final String KEY_SKIPPED = "skipped";
    private static final String KEY_UNKNOWN = "unknown";

    private static final String DIR_EXPORT = "export";
    private static final String DIR_WIDGETS = "widgets";
    private static final String FILE_SUMMARY = "summary.json";
    private static final String KEY_STATISTIC = "statistic";

    public static final String SEPARATOR = "/";

    private FilePathUtils() {
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public static void materializeSummaryForSingleFileReport(final List<FilePath> resultsPaths,
                                                             final FilePath reportDir,
                                                             final PrintStream logger) {
        try {
            final FilePath widgetsSummary = reportDir.child(DIR_WIDGETS).child(FILE_SUMMARY);
            if (widgetsSummary.exists()) {
                return;
            }

            final BuildSummary summary = buildSummaryFromResults(resultsPaths);
            writeSummaryJson(reportDir.child(DIR_WIDGETS).child(FILE_SUMMARY), summary);
            writeSummaryJson(reportDir.child(DIR_EXPORT).child(FILE_SUMMARY), summary);
        } catch (Exception e) {
            logger.printf("Failed to materialize Allure summary for single-file report: %s%n", e);
        }
    }

    private static BuildSummary buildSummaryFromResults(final List<FilePath> resultsPaths)
            throws IOException, InterruptedException {
        final Map<String, Integer> stats = initStats();
        final ObjectMapper mapper = new ObjectMapper();

        for (FilePath resultsPath : resultsPaths) {
            if (resultsPath == null || !resultsPath.exists()) {
                continue;
            }
            processResultsGlob(resultsPath, "**/*-result.json", mapper, stats);
        }

        return new BuildSummary().withStatistics(stats);
    }

    private static Map<String, Integer> initStats() {
        final Map<String, Integer> stats = new HashMap<>(5);
        stats.put(KEY_PASSED, 0);
        stats.put(KEY_FAILED, 0);
        stats.put(KEY_BROKEN, 0);
        stats.put(KEY_SKIPPED, 0);
        stats.put(KEY_UNKNOWN, 0);
        return stats;
    }

    private static void processResultsGlob(final FilePath resultsPath,
                                           final String glob,
                                           final ObjectMapper mapper,
                                           final Map<String, Integer> stats) {
        try {
            for (FilePath f : resultsPath.list(glob)) {
                updateStatsFromResultFile(f, mapper, stats);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logGlobFailure(glob, resultsPath, e);
        } catch (IOException e) {
            logGlobFailure(glob, resultsPath, e);
        }
    }

    private static void logGlobFailure(final String glob, final FilePath resultsPath, final Exception e) {
        LOG.log(Level.FINE, "Unable to process Allure results for glob {0} in {1}: {2}",
                new Object[]{glob, resultsPath.getRemote(), e.toString()});
    }

    private static void updateStatsFromResultFile(final FilePath file,
                                                  final ObjectMapper mapper,
                                                  final Map<String, Integer> stats
    ) throws IOException, InterruptedException {
        try (InputStream is = file.read()) {
            final JsonNode root = mapper.readTree(is);
            final String status = readStatus(root);
            increment(stats, status);
        }
    }

    private static String readStatus(final JsonNode root) {
        final JsonNode statusNode = root.get(KEY_STATUS);
        if (statusNode == null || statusNode.isNull()) {
            return KEY_UNKNOWN;
        }
        return statusNode.asText(KEY_UNKNOWN).toLowerCase(Locale.ROOT);
    }

    private static void increment(final Map<String, Integer> stats, final String status) {
        if (stats.containsKey(status)) {
            stats.put(status, stats.get(status) + 1);
        } else {
            stats.put(KEY_UNKNOWN, stats.get(KEY_UNKNOWN) + 1);
        }
    }

    private static void writeSummaryJson(final FilePath target, final BuildSummary summary)
            throws IOException, InterruptedException {
        final FilePath parent = target.getParent();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode root = mapper.createObjectNode();
        final ObjectNode stat = mapper.createObjectNode();

        stat.put(KEY_PASSED, summary.getPassedCount());
        stat.put(KEY_FAILED, summary.getFailedCount());
        stat.put(KEY_BROKEN, summary.getBrokenCount());
        stat.put(KEY_SKIPPED, summary.getSkipCount());
        stat.put(KEY_UNKNOWN, summary.getUnknownCount());

        root.set(KEY_STATISTIC, stat);

        try (OutputStream os = target.write()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(os, root);
        }
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
            if (previousReport.exists() && isHistoryNotEmpty(previousReport, reportPath)) {
                return previousReport;
            }
            current = current.getPreviousCompletedBuild();
        }
        return null;
    }

    private static boolean isHistoryNotEmpty(final FilePath previousReport,
        final String reportPath) throws IOException {
        try (ZipFile archive = new ZipFile(previousReport.getRemote())) {
            final List<ZipEntry> entries = listEntries(archive, reportPath + "/history/history.json");
            if (Integer.valueOf(entries.size()).equals(1)) {
                final ZipEntry historyEntry = entries.get(0);
                try (InputStream is = archive.getInputStream(historyEntry)) {
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode historyJson = mapper.readTree(is);
                    return historyJson.elements().hasNext();
                }
            }
        }
        return false;
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    public static BuildSummary extractSummary(final Run<?, ?> run, final String reportPath) {
        final FilePath reportZip = new FilePath(run.getArtifactsDir()).child(ALLURE_REPORT_ZIP);

        try {
            if (reportZip.exists()) {
                try (ZipFile archive = new ZipFile(reportZip.getRemote())) {
                    Optional<ZipEntry> summary = getSummary(archive, reportPath, DIR_EXPORT);
                    if (summary.isEmpty()) {
                        summary = getSummary(archive, reportPath, DIR_WIDGETS);
                    }
                    if (summary.isPresent()) {
                        try (InputStream is = archive.getInputStream(summary.get())) {
                            return parseSummaryJson(is);
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException ex) {
            LOG.log(Level.FINE, "Unable to read Allure summary from ZIP for {0}: {1}",
                new Object[]{reportPath, ex.toString()});
        }

        try {
            final FilePath reportDir = new FilePath(run.getRootDir()).child(reportPath);
            FilePath json = reportDir.child(DIR_EXPORT).child(FILE_SUMMARY);
            if (!json.exists()) {
                json = reportDir.child(DIR_WIDGETS).child(FILE_SUMMARY);
            }
            if (json.exists()) {
                try (InputStream is = json.read()) {
                    return parseSummaryJson(is);
                }
            }
        } catch (IOException | InterruptedException ex) {
            LOG.log(Level.FINE, "Unable to read Allure summary from unpacked dir for {0}: {1}",
                new Object[]{reportPath, ex.toString()});
        }

        return new BuildSummary().withStatistics(new HashMap<>());
    }

    private static BuildSummary parseSummaryJson(final InputStream inputStream) throws IOException {
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

    private static int nodeAsInt(final JsonNode node) {
        return (node == null || node.isNull()) ? 0 : node.asInt(0);
    }

    private static Optional<ZipEntry> getSummary(final ZipFile archive,
        final String reportPath,
        final String location) {
        final List<ZipEntry> entries = listEntries(archive, reportPath.concat(SEPARATOR).concat(location));
        final String toSearch = reportPath.concat(SEPARATOR).concat(location).concat(SEPARATOR).concat(FILE_SUMMARY);
        return entries.stream()
            .filter(Objects::nonNull)
            .filter(input -> input.getName().equals(toSearch))
            .findFirst();
    }
}
