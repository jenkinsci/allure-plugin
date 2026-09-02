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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.utils.BuildSummary;
import org.htmlunit.html.HtmlPage;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportProjectActionIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String PADDED_ICON_XPATH = "//img[contains(@src, '/img/icon.png')]";
    private static final String COMPACT_ICON_XPATH = "//img[contains(@src, '/img/icon-compact.png')]";

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
    public void shouldHideAllureActionBeforeFirstReport() throws Exception {
        final FreeStyleProject project = createProject();
        final AllureReportProjectAction action = new AllureReportProjectAction(project);

        assertThat(action.getTarget()).isNull();
        assertThat(action.getIconFileName()).isNull();

        final HtmlPage page = jRule.createWebClient().withJavaScriptEnabled(false).getPage(project);
        assertThat(page.getByXPath(PADDED_ICON_XPATH)).isEmpty();
        assertThat(page.getByXPath(COMPACT_ICON_XPATH)).isEmpty();
    }

    @Test
    public void shouldNotBuildGraphWithSingleAllureBuild() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        final AllureReportProjectAction action = new AllureReportProjectAction(project);
        final AllureReportBuildAction buildAction = build.getAction(AllureReportBuildAction.class);

        assertThat(buildAction).isNotNull();
        assertThat(action.getTarget()).isSameAs(buildAction);
        assertThat(action.getIconFileName()).isNotBlank();
        assertThat(action.getLastAllureBuildAction().getBuildNumber()).isEqualTo(build.getId());
        assertThat(action.isCanBuildGraph()).isFalse();
    }

    @Test
    public void shouldUseContextAppropriateIconsOnProjectPage() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));
        jRule.buildAndAssertSuccess(project);

        final HtmlPage page = jRule.createWebClient().withJavaScriptEnabled(false).getPage(project);

        assertThat(page.getByXPath(PADDED_ICON_XPATH)).hasSize(1);
        assertThat(page.getByXPath(COMPACT_ICON_XPATH)).hasSize(1);
    }

    @Test
    public void shouldTargetLastAllureReportWhenLatestBuildHasNoReport() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        final FreeStyleBuild allureBuild = jRule.buildAndAssertSuccess(project);
        final AllureReportBuildAction buildAction = new AllureReportBuildAction(new BuildSummary(), false);
        allureBuild.addAction(buildAction);
        allureBuild.save();

        project.getBuildersList().add(new FailureBuilder());
        jRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));

        final AllureReportProjectAction action = new AllureReportProjectAction(project);
        assertThat(action.getTarget()).isSameAs(buildAction);
    }

    @Test
    public void shouldReturnLastAllureBuildActionAndEnableGraphAfterTwoBuilds() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);
        final AllureReportProjectAction action = new AllureReportProjectAction(project);

        assertThat(action.getLastAllureBuildAction()).isNotNull();
        assertThat(action.getLastAllureBuildAction().getBuildNumber()).isEqualTo(second.getId());
        assertThat(action.isCanBuildGraph()).isTrue();
    }

    private FreeStyleProject createProject() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", RESULTS_DIR + "/sample-testsuite.xml"));
        return project;
    }
}
