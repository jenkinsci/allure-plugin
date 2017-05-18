package ru.yandex.qatools.allure.jenkins.artifacts;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Run;
import jenkins.model.StandardArtifactManager;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author ehborisov
 */
public class AllureArtifactManager extends StandardArtifactManager {

    private static final String REPORT_ARCHIVE_NAME = "allure-report.zip";

    public AllureArtifactManager(Run<?, ?> build) {
        super(build);
    }

    @Override
    public void archive(FilePath workspace, Launcher launcher, BuildListener listener,
                        final Map<String, String> reportFiles) throws IOException, InterruptedException {
        final File artifactsDir = build.getArtifactsDir();
        artifactsDir.mkdirs();
        ZipStorage.archive(new File(artifactsDir, REPORT_ARCHIVE_NAME), workspace, launcher, listener, reportFiles);
    }
}
