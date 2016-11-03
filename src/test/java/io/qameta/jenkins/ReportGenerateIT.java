package io.qameta.jenkins;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.matrix.Axis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.scm.SCM;
import io.qameta.jenkins.config.AllureReportConfig;
import io.qameta.jenkins.config.ReportBuildPolicy;
import io.qameta.jenkins.config.ResultsConfig;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.jenkins.testdata.TestUtils.assertHasReport;
import static io.qameta.jenkins.testdata.TestUtils.getAllureCommandline;
import static io.qameta.jenkins.testdata.TestUtils.getJdk;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

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

    public static String commandline;

    public static String jdk;

    @BeforeClass
    public static void setUp() throws Exception {
        jdk = getJdk(jRule).getName();
        commandline = getAllureCommandline(jRule, folder).getName();
    }

    @Test
    public void shouldGenerateReport() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher("allure-results"));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertHasReport(build);
    }

    @Test
    public void shouldGenerateReportForMatrixItem() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "a", "b"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher("allure-results"));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getRuns(), hasSize(2));
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertHasReport(run);
        }
    }

    @Test
    public void shouldGenerateAggregatedReportForMatrixJobs() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "first", "second", "third"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher("allure-results"));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);
        assertHasReport(build);
    }

    @Test
    public void shouldGenerateReportOnSlave() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        Label label = new LabelAtom(UUID.randomUUID().toString());
        jRule.createOnlineSlave(label);

        project.setAssignedLabel(label);
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher("allure-results"));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertHasReport(build);
    }

    @Test
    @Ignore("doesn't work properly on windows since allure.bat returns wrong exit code")
    public void shouldFailBuildIfNoResultsFound() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.getPublishersList().add(createAllurePublisher("allure-results"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jRule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void shouldUseDefaultCommandlineIfNotSpecified() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(
                createAllurePublisherWithoutCommandline("allure-results")
        );
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        assertHasReport(build);
    }

    @Test
    public void shouldServeBuildPageWithoutErrors() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher("allure-results"));
        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = jRule.createWebClient();
        HtmlPage page = webClient.getPage(build);
        jRule.assertGoodStatus(page);
    }

    protected SCM getSimpleFileScm(String resourceName, String path) throws IOException {
        //noinspection ConstantConditions
        return new SingleFileSCM(path, getClass().getClassLoader().getResource(resourceName));
    }

    protected AllureReportPublisher createAllurePublisher(String... resultsPaths) throws Exception {
        List<ResultsConfig> paths = Stream.of(resultsPaths).map(ResultsConfig::new).collect(Collectors.toList());
        return new AllureReportPublisher(new AllureReportConfig(
                jdk, commandline, Collections.emptyList(), ReportBuildPolicy.ALWAYS, false, paths
        ));
    }

    protected AllureReportPublisher createAllurePublisherWithoutCommandline(String... resultsPaths) throws Exception {
        List<ResultsConfig> paths = Stream.of(resultsPaths).map(ResultsConfig::new).collect(Collectors.toList());
        return new AllureReportPublisher(new AllureReportConfig(
                jdk, null, Collections.emptyList(), ReportBuildPolicy.ALWAYS, false, paths
        ));
    }
}
