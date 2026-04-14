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
package org.allurereport.jenkins;

import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.allurereport.jenkins.utils.BuildSummary;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArchivedReportTestSupport {

    private ArchivedReportTestSupport() {
    }

    static void switchToRemoteArtifactManager(final FreeStyleBuild build, final File remoteRoot) throws Exception {
        final File localZip = new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        final File remoteZip = new File(remoteRoot, AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        Files.createDirectories(remoteRoot.toPath());
        Files.copy(localZip.toPath(), remoteZip.toPath());
        Files.delete(localZip.toPath());

        final Field artifactManager = Run.class.getDeclaredField("artifactManager");
        artifactManager.setAccessible(true);
        final ArtifactManager manager = new StaticArtifactManager(remoteRoot);
        manager.onLoad(build);
        artifactManager.set(build, manager);
    }

    static FreeStyleBuild buildArchivedReportWithEntries(final Map<String, String> entries,
                                                         final JenkinsRule jRule,
                                                         final String reportPath) throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        writeArchive(build, entries);

        final AllureReportBuildAction action = new AllureReportBuildAction(new BuildSummary(), false);
        action.setReportPath(reportPath);
        build.addAction(action);
        build.save();
        return build;
    }

    static FreeStyleBuild buildDirectoryReportWithEntries(final Map<String, String> entries,
                                                          final JenkinsRule jRule,
                                                          final String reportPath) throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        writeDirectoryReport(build, reportPath, entries);

        final AllureReportBuildAction action = new AllureReportBuildAction(new BuildSummary(), false);
        action.setReportPath(reportPath);
        build.addAction(action);
        build.save();
        return build;
    }

    private static void writeArchive(final FreeStyleBuild build, final Map<String, String> entries) throws Exception {
        final File archive = new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP);
        Files.createDirectories(archive.toPath().getParent());
        try (OutputStream fileOut = Files.newOutputStream(archive.toPath());
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOut.putNextEntry(new ZipEntry(entry.getKey()));
                zipOut.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
    }

    private static void writeDirectoryReport(final FreeStyleBuild build,
                                             final String reportPath,
                                             final Map<String, String> entries) throws Exception {
        final File reportDir = new File(build.getRootDir(), reportPath);
        Files.createDirectories(reportDir.toPath());
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            final File target = new File(reportDir, entry.getKey());
            final File parent = target.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(target.toPath(), entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static final class StaticArtifactManager extends ArtifactManager {

        private final File remoteRoot;

        StaticArtifactManager(final File remoteRoot) {
            this.remoteRoot = remoteRoot;
        }

        @Override
        public void onLoad(final Run<?, ?> run) {
        }

        @Override
        public void archive(final hudson.FilePath workspace,
                            final hudson.Launcher launcher,
                            final hudson.model.BuildListener listener,
                            final Map<String, String> artifacts) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public boolean delete() throws java.io.IOException, InterruptedException {
            Util.deleteRecursive(remoteRoot);
            return true;
        }

        @Override
        public VirtualFile root() {
            return VirtualFile.forFile(remoteRoot);
        }
    }
}
