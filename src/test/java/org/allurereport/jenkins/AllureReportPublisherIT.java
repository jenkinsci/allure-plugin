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
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportPublisherIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String SAMPLE_PASSED = "sample-testsuite.xml";
    private static final String SAMPLE_FAILED = "sample-testsuite-with-failed.xml";
    private static final String HISTORY_ENTRY = "allure-report/history/history.json";
    private static final String EXECUTORS_ENTRY = "allure-report/widgets/executors.json";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_REPORT_URL = "reportUrl";
    private static final String DISPLAY_REDIRECT = "display/redirect";

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

    @Test
    public void shouldUseDisplayUrlForBuildLinksAndClassicUrlForReportLinks() throws Exception {
        final FreeStyleProject project = createProject(SAMPLE_PASSED);
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        final JsonNode executors = archivedJson(build, EXECUTORS_ENTRY);
        final JsonNode executor = executors.get(0);

        assertThat(executor.path("buildUrl").asText()).isEqualTo(DisplayURLProvider.get().getRunURL(build));
        assertThat(executor.path(KEY_REPORT_URL).asText()).isEqualTo(classicReportUrl(build));
        assertThat(executor.path(KEY_REPORT_URL).asText()).doesNotContain(DISPLAY_REDIRECT);

        final JsonNode history = archivedJson(build, HISTORY_ENTRY);
        final List<String> reportUrls = new ArrayList<>();
        for (JsonNode testHistory : history) {
            for (JsonNode item : testHistory.path(KEY_ITEMS)) {
                reportUrls.add(item.path(KEY_REPORT_URL).asText());
            }
        }

        final String reportUrlPattern = "^" + Pattern.quote(classicProjectUrl(project))
                + "\\d+/allure/#testresult/.+$";
        assertThat(reportUrls).isNotEmpty();
        assertThat(reportUrls).allSatisfy(reportUrl -> {
            assertThat(reportUrl).matches(reportUrlPattern);
            assertThat(reportUrl).doesNotContain(DISPLAY_REDIRECT);
        });
    }

    private FreeStyleProject createProject(final String resourceName) throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(resourceName, RESULTS_DIR + "/sample-testsuite.xml"));
        return project;
    }

    private JsonNode archivedJson(final FreeStyleBuild build, final String entryName) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build);
             InputStream inputStream = source.openEntry(entryName)) {
            return mapper.readTree(inputStream);
        }
    }

    private String classicProjectUrl(final FreeStyleProject project) {
        return jRule.jenkins.getRootUrl() + project.getUrl();
    }

    private String classicReportUrl(final FreeStyleBuild build) {
        return jRule.jenkins.getRootUrl() + build.getUrl() + "allure";
    }

    private int historyItems(final FreeStyleBuild build) throws Exception {
        final JsonNode root = archivedJson(build, HISTORY_ENTRY);
        int items = 0;
        for (JsonNode testHistory : root) {
            items += testHistory.path(KEY_ITEMS).size();
        }
        return items;
    }
}
