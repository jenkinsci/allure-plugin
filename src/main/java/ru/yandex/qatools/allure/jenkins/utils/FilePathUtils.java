package ru.yandex.qatools.allure.jenkins.utils;

import hudson.FilePath;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class FilePathUtils {
    public static final String ALLURE_PREFIX = "allure";

    private FilePathUtils() {
    }

    public static List<FilePath> stringsToFilePaths(List<String> strings, FilePath dir) {
        List<FilePath> resultsPaths = new ArrayList<>(strings.size());
        for (String path : strings)
            resultsPaths.add(dir.child(path));
        return resultsPaths;
    }

    public static void copyRecursiveTo(FilePath from, FilePath to, AbstractBuild build, PrintStream logger) throws IOException, InterruptedException { //NOSONAR
        if (from.isRemote() && to.isRemote()) {
            FilePath tmpMasterFilePath = new FilePath(build.getRootDir()).createTempDir(ALLURE_PREFIX, null);
            from.copyRecursiveTo(tmpMasterFilePath);
            tmpMasterFilePath.copyRecursiveTo(to);
            deleteRecursive(tmpMasterFilePath, logger);
        } else {
            from.copyRecursiveTo(to);
        }
    }


    public static void deleteRecursive(FilePath filePath, PrintStream logger) {
        try {
            filePath.deleteContents();
            filePath.deleteRecursive();
        } catch (IOException | InterruptedException e) { //NOSONAR
            logger.println(String.format("Can't delete directory [%s]", filePath));
        }
    }


}
