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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.allurereport.jenkins.config.ResultPolicy;
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.utils.AllureReportArchiveSource;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.InputStream;
import java.util.List;

import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportPublisherIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String SAMPLE_PASSED = "sample-testsuite.xml";
    private static final String SAMPLE_FAILED = "sample-testsuite-with-failed.xml";

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
    public void shouldMarkBuildFailedWhenFailureThresholdCountIsReached() throws Exception {
        final FreeStyleProject project = createProject(SAMPLE_FAILED);
        final AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, RESULTS_DIR);
        publisher.setFailureThresholdCount(1);
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = jRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));

        assertThat(build.getAction(AllureReportBuildAction.class)).isNotNull();
    }

    @Test
    public void shouldLeaveBuildResultUnchangedWhenResultPolicyIsLeaveAsIs() throws Exception {
        final FreeStyleProject project = createProject(SAMPLE_FAILED);
        final AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, RESULTS_DIR);
        publisher.setResultPolicy(ResultPolicy.LEAVE_AS_IS);
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getAction(AllureReportBuildAction.class)).isNotNull();
        assertThat(build.getResult()).isEqualTo(Result.SUCCESS);
    }

    @Test
    public void shouldArchiveCustomReportPathUsingLeafDirectoryName() throws Exception {
        final FreeStyleProject project = createProject(SAMPLE_PASSED);
        final AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, RESULTS_DIR);
        publisher.setReport("target/custom-report");
        project.getPublishersList().add(publisher);

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build)) {
            final List<String> entries = source.listEntries("custom-report");
            assertThat(entries).contains("custom-report/index.html");
        }
    }

    @Test
    public void shouldCarryHistoryAcrossBuilds() throws Exception {
        final FreeStyleProject project = createProject(SAMPLE_PASSED);
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        final FreeStyleBuild first = jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);

        final int firstHistoryItems = historyItems(first);
        final int secondHistoryItems = historyItems(second);

        assertThat(firstHistoryItems).isGreaterThan(0);
        assertThat(secondHistoryItems).isGreaterThan(firstHistoryItems);
    }

    private FreeStyleProject createProject(final String resourceName) throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(resourceName, RESULTS_DIR + "/sample-testsuite.xml"));
        return project;
    }

    private int historyItems(final FreeStyleBuild build) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build);
             InputStream inputStream = source.openEntry("allure-report/history/history.json")) {
            final JsonNode root = mapper.readTree(inputStream);
            int items = 0;
            for (JsonNode testHistory : root) {
                items += testHistory.path("items").size();
            }
            return items;
        }
    }
}
