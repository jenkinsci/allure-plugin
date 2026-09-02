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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import org.allurereport.jenkins.tools.AllureInstallation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ClassDataAbstractionCoupling")
public class ReportBuilderTest {

    private static final String VERSION_3 = "3.1.0";
    private static final String EXECUTABLE = "/opt/allure/bin/allure";
    private static final String GENERATE = "generate";
    private static final String CLEAN = "-c";
    private static final String OUTPUT = "-o";
    private static final String CONFIG = "--config";
    private static final String SINGLE_FILE = "--single-file";
    private static final String QUOTE = "\"";
    private static final String WINDOWS_NODE = "C:\\tools\\node.exe";
    private static final String WINDOWS_CLI = "C:\\tools\\allure\\cli.js";
    private static final String WINDOWS_BATCH = "C:\\tools\\allure.cmd";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void parseMajorDefaultsToAllure2ForNullAndUnparseableVersions() throws Exception {
        assertThat(parseMajor(null)).isEqualTo(2);
        assertThat(parseMajor("not-a-version")).isEqualTo(2);
    }

    @Test
    public void parseMajorExtractsFirstNumericVersionComponent() throws Exception {
        assertThat(parseMajor(VERSION_3)).isEqualTo(3);
        assertThat(parseMajor(" 1.4.24 ")).isEqualTo(1);
        assertThat(parseMajor("v2.32.0")).isEqualTo(2);
    }

    @Test
    public void allure2ArgumentsIncludeCleanConfigAndSingleFileFlags() throws Exception {
        final FilePath results1 = new FilePath(folder.newFolder("results-one"));
        final FilePath results2 = new FilePath(folder.newFolder("results-two"));
        final FilePath report = new FilePath(folder.newFolder("report-dir"));
        final FilePath config = new FilePath(folder.newFile("allure.yaml"));

        final ArgumentListBuilder arguments = invokeArguments(
                "2.35.1",
                Arrays.asList(results1, results2),
                report,
                config,
                true
        );

        assertThat(arguments.toList()).containsExactly(
                EXECUTABLE,
                GENERATE,
                results1.getRemote(),
                results2.getRemote(),
                CLEAN,
                OUTPUT,
                report.getRemote(),
                CONFIG,
                config.getRemote(),
                SINGLE_FILE
        );
    }

    @Test
    public void allure3ArgumentsDoNotIncludeCleanFlag() throws Exception {
        final FilePath results = new FilePath(folder.newFolder("results"));
        final FilePath report = new FilePath(folder.newFolder("report"));

        final ArgumentListBuilder arguments = invokeArguments(
                VERSION_3,
                Arrays.asList(results),
                report,
                null,
                false
        );

        assertThat(arguments.toList()).containsExactly(
                EXECUTABLE,
                GENERATE,
                results.getRemote(),
                OUTPUT,
                report.getRemote()
        );
        assertThat(arguments.toList()).doesNotContain(CLEAN, CONFIG, SINGLE_FILE);
    }

    @Test
    public void managedAllure3UsesNodeAndCliAsSeparateArguments() throws Exception {
        final File resultsDirectory = folder.newFolder("results&whoami&");
        final File reportDirectory = new File(folder.getRoot(), "report&whoami&");
        final FilePath results = new FilePath(resultsDirectory);
        final FilePath report = new FilePath(reportDirectory);

        final ArgumentListBuilder arguments = invokeArguments(
                VERSION_3,
                Collections.singletonList(results),
                report,
                null,
                false,
                new FakeAllureInstallation(VERSION_3, Arrays.asList(WINDOWS_NODE, WINDOWS_CLI))
        );

        assertThat(arguments.toList()).containsExactly(
                WINDOWS_NODE,
                WINDOWS_CLI,
                GENERATE,
                results.getRemote(),
                OUTPUT,
                report.getRemote()
        );
    }

    @Test
    public void windowsBatchArgumentsEscapeCommandMetacharacters() {
        final ArgumentListBuilder raw = new ArgumentListBuilder();
        raw.add(WINDOWS_BATCH, GENERATE, "C:\\workspace\\results&whoami&");

        final ArgumentListBuilder escaped = ReportBuilder.protectWindowsBatchCommand(
                raw,
                WINDOWS_BATCH,
                false
        );

        assertThat(escaped.toList()).startsWith("cmd.exe", "/C");
        assertThat(String.join(" ", escaped.toList()))
                .contains("\"C:\\workspace\\results&whoami&\"");
    }

    @Test
    public void allure1ArgumentsQuotePathsWithSpaces() throws Exception {
        final FilePath results = new FilePath(folder.newFolder("results with spaces"));
        final FilePath report = new FilePath(folder.newFolder("report with spaces"));

        final ArgumentListBuilder arguments = invokeArguments(
                "1.5.4",
                Arrays.asList(results),
                report,
                null,
                false
        );

        assertThat(arguments.toList()).containsExactly(
                EXECUTABLE,
                GENERATE,
                QUOTE + results.getRemote() + QUOTE,
                OUTPUT,
                QUOTE + report.getRemote() + QUOTE
        );
        assertThat(arguments.toStringWithQuote())
                .contains(QUOTE + results.getRemote() + QUOTE)
                .contains(QUOTE + report.getRemote() + QUOTE);
    }

    private int parseMajor(final String version) throws Exception {
        final Method method = ReportBuilder.class.getDeclaredMethod("parseMajor", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, version);
    }

    private ArgumentListBuilder invokeArguments(final String version,
                                                final List<FilePath> resultsPaths,
                                                final FilePath reportPath,
                                                final FilePath configFilePath,
                                                final boolean singleFile) throws Exception {
        final StreamTaskListener listener = new StreamTaskListener(System.out, StandardCharsets.UTF_8);
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        final FilePath workspace = new FilePath(folder.newFolder("workspace-" + version.replace('.', '-')));
        final ReportBuilder builder = new ReportBuilder(
                launcher,
                listener,
                workspace,
                new EnvVars(),
                new FakeAllureInstallation(version)
        );

        if (configFilePath != null) {
            builder.setConfigFilePath(configFilePath);
        }
        builder.setSingleFile(singleFile);

        return builder.getArguments(version, resultsPaths, reportPath);
    }

    private ArgumentListBuilder invokeArguments(final String version,
                                                final List<FilePath> resultsPaths,
                                                final FilePath reportPath,
                                                final FilePath configFilePath,
                                                final boolean singleFile,
                                                final AllureInstallation installation) throws Exception {
        final StreamTaskListener listener = new StreamTaskListener(System.out, StandardCharsets.UTF_8);
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        final FilePath workspace = new FilePath(folder.newFolder("workspace-prefix"));
        final ReportBuilder builder = new ReportBuilder(
                launcher,
                listener,
                workspace,
                new EnvVars(),
                installation
        );
        if (configFilePath != null) {
            builder.setConfigFilePath(configFilePath);
        }
        builder.setSingleFile(singleFile);
        return builder.getArguments(version, resultsPaths, reportPath);
    }

    private static final class FakeAllureInstallation implements AllureInstallation {

        private final String version;
        private final List<String> commandPrefix;

        private FakeAllureInstallation(final String version) {
            this(version, Collections.singletonList(EXECUTABLE));
        }

        private FakeAllureInstallation(final String version, final List<String> commandPrefix) {
            this.version = version;
            this.commandPrefix = commandPrefix;
        }

        @Override
        public String getExecutable(final Launcher launcher) {
            return EXECUTABLE;
        }

        @Override
        public List<String> getCommandPrefix(final Launcher launcher) {
            return commandPrefix;
        }

        @Override
        public String getMajorVersion(final Launcher launcher) {
            return version;
        }

        @Override
        public String getName() {
            return "fake";
        }
    }
}
