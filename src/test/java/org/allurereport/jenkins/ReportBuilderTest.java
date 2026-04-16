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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportBuilderTest {

    private static final String VERSION_3 = "3.1.0";
    private static final String EXECUTABLE = "/opt/allure/bin/allure";
    private static final String GENERATE = "generate";
    private static final String CLEAN = "-c";
    private static final String OUTPUT = "-o";
    private static final String CONFIG = "--config";
    private static final String SINGLE_FILE = "--single-file";
    private static final String QUOTE = "\"";

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

        final Method method = ReportBuilder.class.getDeclaredMethod(
                "getArguments", String.class, List.class, FilePath.class
        );
        method.setAccessible(true);
        return (ArgumentListBuilder) method.invoke(builder, version, resultsPaths, reportPath);
    }

    private static final class FakeAllureInstallation implements AllureInstallation {

        private final String version;

        private FakeAllureInstallation(final String version) {
            this.version = version;
        }

        @Override
        public String getExecutable(final Launcher launcher) {
            return EXECUTABLE;
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
