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
package org.allurereport.jenkins.tools;

import hudson.Functions;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ClassDataAbstractionCoupling")
public class AllureManagedInstallerRuntimeTest {

    private static final int EXECUTABLE_MODE = 493;
    private static final String NODE = "node";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void installsAndRunsBundledRecommendationWithoutNpmAccess() throws Exception {
        Assume.assumeFalse(Functions.isWindows());
        final NodePlatform platform = NodePlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                false
        );
        final Path archive = createNodeArchive(platform);
        final AllureManagedInstaller installer = new OfflineInstaller(platform, archive);
        final AllureCommandlineInstallation installation = installation(installer);

        installer.performInstallation(installation, jenkins.jenkins, TaskListener.NULL);

        final Launcher launcher = new Launcher.LocalLauncher(TaskListener.NULL);
        final String executable = installation.getExecutable(launcher);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int exitCode = launcher.launch()
                .cmds(executable, "--version")
                .stdout(output)
                .join();

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8).trim())
                .isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION);
        assertThat(Paths.get(executable)).isExecutable();

        final Path results = folder.newFolder("allure-results").toPath();
        Files.writeString(
                results.resolve("sample-result.json"),
                "{\"uuid\":\"sample\",\"historyId\":\"sample\",\"name\":\"sample\","
                        + "\"fullName\":\"sample\",\"status\":\"passed\",\"stage\":\"finished\","
                        + "\"steps\":[],\"attachments\":[],\"parameters\":[],\"labels\":[],\"links\":[],"
                        + "\"start\":1,\"stop\":2}",
                StandardCharsets.UTF_8
        );
        final Path report = folder.getRoot().toPath().resolve("allure-report");
        final ByteArrayOutputStream generationOutput = new ByteArrayOutputStream();
        final int generationExitCode = launcher.launch()
                .cmds(executable, "generate", results.toString(), "-o", report.toString())
                .stdout(generationOutput)
                .join();

        assertThat(generationExitCode)
                .as(generationOutput.toString(StandardCharsets.UTF_8))
                .isZero();
        assertThat(report.resolve("index.html")).isRegularFile();

        installer.performInstallation(installation, jenkins.jenkins, TaskListener.NULL);
        assertThat(installation.getExecutable(launcher)).isEqualTo(executable);
    }

    private AllureCommandlineInstallation installation(final AllureManagedInstaller installer) throws Exception {
        final InstallSourceProperty source = new InstallSourceProperty(Collections.singletonList(installer));
        final List<ToolProperty<?>> properties = Collections.singletonList(source);
        return new AllureCommandlineInstallation(
                "Allure",
                folder.newFolder("allure-tool").getAbsolutePath(),
                properties
        );
    }

    private Path createNodeArchive(final NodePlatform platform) throws IOException {
        final Path buildNode = Paths.get(
                System.getProperty("basedir"),
                "target",
                "allure3-build-node",
                NODE,
                NODE
        ).toAbsolutePath();
        assertThat(buildNode).isExecutable();

        final byte[] launcher = ("#!/bin/sh\nexec \"" + buildNode + "\" \"$@\"\n")
                .getBytes(StandardCharsets.UTF_8);
        final Path archive = folder.newFile(platform.archiveFileName()).toPath();
        try (OutputStream file = Files.newOutputStream(archive);
             GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(file);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            final TarArchiveEntry entry = new TarArchiveEntry(platform.nodeExecutableRelativePath());
            entry.setMode(EXECUTABLE_MODE);
            entry.setSize(launcher.length);
            tar.putArchiveEntry(entry);
            tar.write(launcher);
            tar.closeArchiveEntry();
        }
        return archive;
    }

    private static final class OfflineInstaller extends AllureManagedInstaller {
        private final NodePlatform platform;
        private final Path archive;

        private OfflineInstaller(final NodePlatform platform, final Path archive) {
            super(VERSION_POLICY_RECOMMENDED);
            this.platform = platform;
            this.archive = archive;
        }

        @Override
        NodePlatform detectPlatform(final Node node) {
            return platform;
        }

        @Override
        Path acquireNodeArchive(final NodePlatform ignored, final TaskListener log) {
            return archive;
        }
    }
}
