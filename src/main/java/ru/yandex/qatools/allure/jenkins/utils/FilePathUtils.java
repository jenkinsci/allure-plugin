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
package ru.yandex.qatools.allure.jenkins.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public final class FilePathUtils {

    private static final String ALLURE_PREFIX = "allure";
    private static final String ALLURE_REPORT_ZIP = "allure-report.zip";
    private static final List<String> BUILD_STATISTICS_KEYS = Arrays.asList(
            "passed",
            "failed",
            "broken",
            "skipped",
            "unknown");
    public static final String SEPORATOR = "/";

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
        final FilePath report = new FilePath(run.getArtifactsDir()).child(ALLURE_REPORT_ZIP);
        try {
            if (!report.exists()) {
                return null;
            }
            try (ZipFile archive = new ZipFile(report.getRemote())) {

                Optional<ZipEntry> summary = getSummary(archive, reportPath, "export");
                if (!summary.isPresent()) {
                    summary = getSummary(archive, reportPath, "widgets");
                }
                if (summary.isPresent()) {
                    try (InputStream is = archive.getInputStream(summary.get())) {
                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode summaryJson = mapper.readTree(is);
                        final JsonNode statisticJson = summaryJson.get("statistic");
                        final Map<String, Integer> statisticsMap = new HashMap<>();
                        for (String key : BUILD_STATISTICS_KEYS) {
                            statisticsMap.put(key, statisticJson.get(key).intValue());
                        }
                        return new BuildSummary().withStatistics(statisticsMap);
                    }
                }
            }
        } catch (IOException | InterruptedException ignore) {
            // nothing to do
        }
        return null;
    }

    private static Optional<ZipEntry> getSummary(final ZipFile archive,
                                                 final String reportPath,
                                                 final String location) {
        final List<ZipEntry> entries = listEntries(archive, reportPath.concat(SEPORATOR).concat(location));
        final String toSearch = reportPath.concat(SEPORATOR).concat(location).concat("/summary.json");
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(input -> input.getName().equals(toSearch))
                .findFirst();
    }
}
