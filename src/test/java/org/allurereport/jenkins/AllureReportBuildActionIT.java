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
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.utils.AllureReportArchiveSource;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportBuildActionIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String INDEX_ENTRY = "allure-report/index.html";

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
    public void shouldRenderBuildBadgeAndServeReportFromArchivedZip() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final HtmlPage buildPage = webClient.getPage(build);
        assertThat(buildPage.getByXPath("//a[contains(@href, '/allure')]")).isNotEmpty();
        assertThat(new File(build.getRootDir(), "allure-report")).doesNotExist();

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + "allure/index.html"))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(readArchivedEntry(build, INDEX_ENTRY));
    }

    @Test
    public void shouldDownloadIndexFromArchivedZip() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + "allure/downloadIndex"))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getResponseHeaderValue("Content-Disposition"))
                .contains("attachment; filename=\"index.html\"");
        assertThat(response.getContentAsString()).isEqualTo(readArchivedEntry(build, INDEX_ENTRY));
    }

    @Test
    public void shouldRejectPathTraversalRequests() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + "allure/..;/secret.txt", 400);
    }

    @Test
    public void shouldExposeGraphEndpointsAfterTwoReportBuilds() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse graph = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), second.getUrl() + "allure/graph"))
        );
        final WebResponse graphMap = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), second.getUrl() + "allure/graphMap"))
        );

        assertThat(graph.getStatusCode()).isEqualTo(200);
        assertThat(graph.getContentType()).isEqualTo("image/png");
        assertThat(graphMap.getStatusCode()).isEqualTo(200);
        assertThat(graphMap.getContentAsString()).contains("<area");
    }

    private FreeStyleBuild buildSingleReportBuild() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));
        return jRule.buildAndAssertSuccess(project);
    }

    private FreeStyleProject createProject() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", RESULTS_DIR + "/sample-testsuite.xml"));
        return project;
    }

    private String readArchivedEntry(final FreeStyleBuild build, final String entryPath) throws Exception {
        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build);
             InputStream inputStream = source.openEntry(entryPath)) {
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
