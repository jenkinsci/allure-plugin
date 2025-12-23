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
import hudson.model.JDK;
import hudson.util.StreamTaskListener;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.tools.AllureCommandlineInstallation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author charlie (Dmitry Baev).
 */
public class CommandlineIT {

    public static final String SAMPLE_TESTSUITE_FILE_NAME = "sample-testsuite.xml";
    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static ReportBuilder builder;

    @BeforeClass
    public static void setUp() throws Exception {
        final EnvVars envVars = new EnvVars();
        final JDK jdk = TestUtils.getJdk(jRule);
        jdk.buildEnvVars(envVars);
        final AllureCommandlineInstallation allure = TestUtils.getAllureCommandline(jRule, folder);
        final StreamTaskListener listener = new StreamTaskListener(System.out, StandardCharsets.UTF_8);
        final Launcher launcher = new Launcher.LocalLauncher(listener);
        final FilePath workspace = new FilePath(folder.newFolder());
        workspace.mkdirs();
        builder = new ReportBuilder(launcher, listener, workspace, envVars, allure);
    }

    @Test
    public void shouldGenerateReport() throws Exception {
        final FilePath results = new FilePath(folder.newFolder("some with spaces in path (and even more x8)"));
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SAMPLE_TESTSUITE_FILE_NAME)) {
            results.child(SAMPLE_TESTSUITE_FILE_NAME).copyFrom(is);
        }
        final FilePath report = new FilePath(folder.getRoot()).child("some folder with (x22) spaces");
        final int exitCode = builder.build(Collections.singletonList(results), report);
        assertThat(exitCode).as("Should exit with code 0").isEqualTo(0);
    }
}
