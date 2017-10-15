package ru.yandex.qatools.allure.jenkins.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public final class FilePathUtils {

    private static final String ALLURE_PREFIX = "allure";

    private static final List<String> BUILD_STATISTICS_KEYS = Arrays.asList(
            "passed",
            "failed",
            "broken",
            "skipped",
            "unknown");

    private FilePathUtils() {
    }

    @SuppressWarnings("TrailingComment")
    public static void copyRecursiveTo(FilePath from, FilePath to, AbstractBuild build, PrintStream logger)
            throws IOException, InterruptedException { //NOSONAR
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
    public static void deleteRecursive(FilePath filePath, PrintStream logger) {
        try {
            filePath.deleteContents();
            filePath.deleteRecursive();
        } catch (IOException | InterruptedException e) { //NOSONAR
            logger.println(String.format("Can't delete directory [%s]", filePath));
        }
    }

    public static FilePath getPreviousReport(Run<?, ?> run) throws IOException, InterruptedException {
        Run<?, ?> current = run;
        while (current != null) {
            final FilePath previousReport = new FilePath(current.getRootDir()).child("archive/allure-report.zip");
            if (previousReport.exists()) {
                return previousReport;
            }
            current = current.getPreviousCompletedBuild();
        }
        return null;
    }

    public static BuildSummary extractSummary(final Run<?, ?> run, final String reportPath) {
        final FilePath report = new FilePath(run.getRootDir()).child("archive/allure-report.zip");
        try {
            if (!report.exists()) {
                return null;
            }
            try (ZipFile archive = new ZipFile(report.getRemote())) {
                List<ZipEntry> entries = listEntries(archive, reportPath + "/export");
                Optional<ZipEntry> summary = Iterables.tryFind(entries, new Predicate<ZipEntry>() {
                    @Override
                    public boolean apply(@Nullable ZipEntry input) {
                        return input != null && input.getName().equals(reportPath + "/export/summary.json");
                    }
                });
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
        }
        return null;
    }
}
