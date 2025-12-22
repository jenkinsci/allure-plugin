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
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.allurereport.jenkins.testdata.TestUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author charlie (Dmitry Baev).
 */
public class PipelineIT {

    private static final String ALLURE_RESULTS = "allure-results";
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

    public static String getCommandline() {
        return commandline;
    }

    public static void setCommandline(final String commandline) {
        PipelineIT.commandline = commandline;
    }

    public static String getJdk() {
        return jdk;
    }

    public static void setJdk(String jdk) {
        PipelineIT.jdk = jdk;
    }

    @Test
    public void shouldSupportPipeline() throws Exception {
        final WorkflowJob project = jRule.createProject(WorkflowJob.class);
        prepareWorkspace(project);

        final FlowDefinition definition =
                new CpsFlowDefinition("node { allure(results: [[path: 'allure-results']]) }", true);
        project.setDefinition(definition);
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("paths", ALLURE_RESULTS)
        ));
        final WorkflowRun build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    private void prepareWorkspace(final WorkflowJob project) throws IOException, InterruptedException {
        final FilePath workspace = jRule.jenkins.getWorkspaceFor(project);
        final String testSuiteFileName = "sample-testsuite.xml";
        final FilePath allureReportsDir = workspace.child(ALLURE_RESULTS).child(testSuiteFileName);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(testSuiteFileName)) {
            allureReportsDir.copyFrom(is);
        }
    }
}
