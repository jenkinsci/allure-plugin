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

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.ArtifactArchiver;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.util.VirtualFile;
import org.allurereport.jenkins.config.ReportStorage;
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.utils.AllureReportArchiveSource;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class LocalReportStorageIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String SAMPLE_PASSED = "sample-testsuite.xml";
    private static final String SAMPLE_RESULTS_PATH = RESULTS_DIR + "/sample-testsuite.xml";
    private static final String REPORT_PATH = "allure-report";
    private static final String REPORT_INDEX = REPORT_PATH + "/index.html";
    private static final String OTHER_ARTIFACT = "other.txt";
    private static final String REMOTE_ARTIFACTS = "remote-artifacts";
    private static final String SUMMARY_ARTIFACT = "allure-summary.json";

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void storesAllureLocallyWithoutCallingArtifactManagerAgain() throws Exception {
        configureSingleArchiveArtifactManager();
        final String jdk = TestUtils.getJdk(jRule).getName();
        final String commandline = TestUtils.getAllureCommandline(jRule, folder).getName();
        final AllureReportPublisherDescriptor descriptor = jRule.jenkins
                .getDescriptorByType(AllureReportPublisherDescriptor.class);
        descriptor.setReportStorage(ReportStorage.LOCAL_ON_CONTROLLER);

        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_PASSED, SAMPLE_RESULTS_PATH));
        project.getBuildersList().add(new OtherArtifactBuilder());
        project.getPublishersList().add(new ArtifactArchiver(OTHER_ARTIFACT));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(SingleArchiveArtifactManagerFactory.archiveCalls()).isEqualTo(1);
        assertThat(build.getArtifactManager().root().child(OTHER_ARTIFACT).exists()).isTrue();
        assertThat(build.getArtifactManager().root()
                .child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP).exists()).isFalse();
        assertThat(new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP))
                .isFile();
        assertThat(new File(build.getArtifactsDir(), SUMMARY_ARTIFACT)).isFile();
        jRule.assertLogContains("Allure report stored locally on Jenkins controller.", build);

        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build)) {
            final List<String> entries = source.listEntries(REPORT_PATH);
            assertThat(entries).contains(REPORT_INDEX);
        }
    }

    @Test
    public void archivesAllureThroughArtifactManagerByDefault() throws Exception {
        configureSingleArchiveArtifactManager();
        final String jdk = TestUtils.getJdk(jRule).getName();
        final String commandline = TestUtils.getAllureCommandline(jRule, folder).getName();

        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_PASSED, SAMPLE_RESULTS_PATH));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(SingleArchiveArtifactManagerFactory.archiveCalls()).isEqualTo(1);
        assertThat(build.getArtifactManager().root()
                .child(AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP).exists()).isTrue();
        assertThat(build.getArtifactManager().root().child(SUMMARY_ARTIFACT).exists()).isTrue();
        assertThat(new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP))
                .doesNotExist();
        jRule.assertLogContains("Allure artifact archived via ArtifactManager.", build);

        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build)) {
            final List<String> entries = source.listEntries(REPORT_PATH);
            assertThat(entries).contains(REPORT_INDEX);
        }
    }

    private void configureSingleArchiveArtifactManager() {
        SingleArchiveArtifactManagerFactory.reset();
        ArtifactManagerConfiguration.get().getArtifactManagerFactories()
                .add(new SingleArchiveArtifactManagerFactory());
    }

    public static class SingleArchiveArtifactManagerFactory extends ArtifactManagerFactory {

        private static final AtomicInteger ARCHIVE_CALLS = new AtomicInteger();

        static void reset() {
            ARCHIVE_CALLS.set(0);
        }

        static int archiveCalls() {
            return ARCHIVE_CALLS.get();
        }

        @Override
        public ArtifactManager managerFor(final Run<?, ?> run) {
            return new SingleArchiveArtifactManager(new File(run.getRootDir(), REMOTE_ARTIFACTS));
        }

        @TestExtension
        public static class DescriptorImpl extends ArtifactManagerFactoryDescriptor {

            @Override
            public String getDisplayName() {
                return "Single archive Artifact Manager";
            }
        }

        private static final class SingleArchiveArtifactManager extends ArtifactManager {

            private final String rootPath;

            SingleArchiveArtifactManager(final File root) {
                this.rootPath = root.getAbsolutePath();
            }

            @Override
            public void onLoad(final Run<?, ?> run) {
            }

            @Override
            public void archive(final FilePath workspace,
                                final Launcher launcher,
                                final BuildListener listener,
                                final Map<String, String> artifacts) throws IOException, InterruptedException {
                final int invocation = ARCHIVE_CALLS.incrementAndGet();
                if (invocation > 1) {
                    throw new IOException("Artifact Manager was invoked more than once");
                }

                final FilePath root = new FilePath(new File(rootPath));
                root.mkdirs();
                for (Map.Entry<String, String> artifact : artifacts.entrySet()) {
                    final FilePath target = root.child(artifact.getKey());
                    final FilePath parent = target.getParent();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    workspace.child(artifact.getValue()).copyTo(target);
                }
            }

            @Override
            public boolean delete() throws IOException {
                Util.deleteRecursive(new File(rootPath));
                return true;
            }

            @Override
            public VirtualFile root() {
                return VirtualFile.forFile(new File(rootPath));
            }
        }
    }

    private static final class OtherArtifactBuilder extends TestBuilder {

        @Override
        public boolean perform(final AbstractBuild<?, ?> build,
                               final Launcher launcher,
                               final BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child(OTHER_ARTIFACT).write("other artifact", "UTF-8");
            return true;
        }
    }
}
