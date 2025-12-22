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

import hudson.matrix.Axis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.Result;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.allurereport.jenkins.testdata.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;

/**
 * eroshenkoam.
 * 01.11.17
 */
public class MatrixIT {

    private static final String ALLURE_RESULTS = "allure-results/sample-testsuite.xml";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static String commandline;

    private static String jdk;

    @BeforeClass
    public static void setUp() throws Exception {
        jdk = TestUtils.getJdk(jRule).getName();
        commandline = TestUtils.getAllureCommandline(jRule, folder).getName();
    }

    @Test
    public void shouldGenerateReportForMatrixItem() throws Exception {
        final MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("items", "first", "second"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));

        final MatrixBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
        assertThat(build.getRuns()).hasSize(2);
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureReportBuildAction.class)).hasSize(1);
        }
    }




}
