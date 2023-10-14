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
package ru.yandex.qatools.allure.jenkins;

import org.htmlunit.html.HtmlPage;
import hudson.matrix.Axis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.labels.LabelAtom;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.testdata.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.qatools.allure.jenkins.testdata.TestUtils.createAllurePublisher;
import static ru.yandex.qatools.allure.jenkins.testdata.TestUtils.createAllurePublisherWithoutCommandline;
import static ru.yandex.qatools.allure.jenkins.testdata.TestUtils.getSimpleFileScm;

/**
 * @author charlie (Dmitry Baev).
 */
@SuppressWarnings("ClassDataAbstractionCoupling")
public class ReportGenerateIT {

    public static final String ALLURE_RESULTS = "allure-results/sample-testsuite.xml";
    private static final String SAMPLE_TESTSUITE_FILE_NAME = "sample-testsuite.xml";
    private static final String ALLURE_RESULTS_PATH = "allure-results";

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
    public void shouldGenerateReport() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldCreateArtifact() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getArtifacts())
                .as("An artifact for allure report should be created in the artifacts dir for the build")
                .hasSize(1);
    }

    @Test
    public void shouldGenerateReportForParameters() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("RESULTS", ALLURE_RESULTS_PATH)));
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "${RESULTS}"));
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportForWrappedParameters() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        final AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH);
        final List<PropertyConfig> properties = new ArrayList<>();
        properties.add(new PropertyConfig("allure.tests.management.pattern", "http://tms.test?a=f&s=123"));
        publisher.setProperties(properties);
        project.getPublishersList().add(publisher);
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportInCustomReportPath() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        final AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH);
        publisher.setReport("target/report");
        project.getPublishersList().add(publisher);
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportWithUnstableResult() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite-with-failed.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));
        final FreeStyleBuild build = jRule.assertBuildStatus(Result.UNSTABLE, project.scheduleBuild2(0));

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportForGlob() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, "target/".concat(ALLURE_RESULTS)));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "**/allure-results"));
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportForMatrixItem() throws Exception {
        final MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("items", "first", "second"));
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));

        final MatrixBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
        assertThat(build.getRuns()).hasSize(2);
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureReportBuildAction.class)).hasSize(1);
        }
    }

    @Test
    public void shouldGenerateReportOnSlave() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();

        final Label label = new LabelAtom(UUID.randomUUID().toString());
        jRule.createOnlineSlave(label);

        project.setAssignedLabel(label);
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));

        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    @Ignore("doesn't work properly on windows since allure.bat returns wrong exit code")
    public void shouldFailBuildIfNoResultsFound() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));

        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void shouldUseDefaultCommandlineIfNotSpecified() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(
                createAllurePublisherWithoutCommandline(jdk, ALLURE_RESULTS_PATH)
        );
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldServeBuildPageWithoutErrors() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm(SAMPLE_TESTSUITE_FILE_NAME, ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, ALLURE_RESULTS_PATH));
        final FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        final JenkinsRule.WebClient webClient = jRule.createWebClient();
        final HtmlPage page = webClient.getPage(build);
        jRule.assertGoodStatus(page);
    }
}
