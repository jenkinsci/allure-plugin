package ru.yandex.qatools.allure.jenkins.utils;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import io.qameta.allure.summary.SummaryData;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ru.yandex.qatools.allure.jenkins.utils.ZipUtils.listEntries;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public final class FilePathUtils {

    private static final String ALLURE_PREFIX = "allure";

    private static final Type SUMMARY_DATA_TYPE = new TypeToken<SummaryData>() {
    }.getType();

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

    public static SummaryData extractSummary(final Run<?, ?> run) {
        final FilePath report = new FilePath(run.getRootDir()).child("archive/allure-report.zip");
        try {
            if (!report.exists()) {
                return null;
            }
            try (ZipFile archive = new ZipFile(report.getRemote())) {
                List<ZipEntry> entries = listEntries(archive, "allure-report/export");
                Optional<ZipEntry> summary = Iterables.tryFind(entries, new Predicate<ZipEntry>() {
                    @Override
                    public boolean apply(@Nullable ZipEntry input) {
                        return input != null && input.getName().equals("allure-report/export/summary.json");
                    }
                });
                if (summary.isPresent()) {
                    try (InputStream is = archive.getInputStream(summary.get())) {
                        Gson gson = new Gson();
                        return gson.fromJson(new BufferedReader(new InputStreamReader(is)),
                                SUMMARY_DATA_TYPE);
                    }
                }
            }
        } catch (IOException | InterruptedException ignore) {
        }
        return null;
    }
}
