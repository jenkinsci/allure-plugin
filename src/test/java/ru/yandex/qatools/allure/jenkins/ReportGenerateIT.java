package ru.yandex.qatools.allure.jenkins;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
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
public class ReportGenerateIT {

    public static final String ALLURE_RESULTS = "allure-results/sample-testsuite.xml";

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
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldCreateArtifact() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getArtifacts())
                .as("An artifact for allure report should be created in the artifacts dir for the build")
                .hasSize(1);
    }

    @Test
    public void shouldGenerateReportForParameters() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("RESULTS", "allure-results")));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "${RESULTS}"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportForWrappedParameters() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, "allure-results");
        List<PropertyConfig> properties = new ArrayList<>();
        properties.add(new PropertyConfig("allure.tests.management.pattern", "http://tms.test?a=f&s=123"));
        publisher.setProperties(properties);
        project.getPublishersList().add(publisher);
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportInCustomReportPath() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        AllureReportPublisher publisher = createAllurePublisher(jdk, commandline, "allure-results");
        publisher.setReport("target/report");
        project.getPublishersList().add(publisher);
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportWithUnstableResult() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite-with-failed.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));
        FreeStyleBuild build = jRule.assertBuildStatus(Result.UNSTABLE, project.scheduleBuild2(0));

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportForGlob() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", "target/".concat(ALLURE_RESULTS)));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "**/allure-results"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldGenerateReportOnSlave() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        Label label = new LabelAtom(UUID.randomUUID().toString());
        jRule.createOnlineSlave(label);

        project.setAssignedLabel(label);
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    @Ignore("doesn't work properly on windows since allure.bat returns wrong exit code")
    public void shouldFailBuildIfNoResultsFound() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void shouldUseDefaultCommandlineIfNotSpecified() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(
                createAllurePublisherWithoutCommandline(jdk, "allure-results")
        );
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
    }

    @Test
    public void shouldServeBuildPageWithoutErrors() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = jRule.createWebClient();
        HtmlPage page = webClient.getPage(build);
        jRule.assertGoodStatus(page);
    }
}
