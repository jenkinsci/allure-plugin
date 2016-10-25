package io.qameta.jenkins;

import hudson.FilePath;
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
import io.qameta.jenkins.tools.AllureInstallation;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * @author charlie (Dmitry Baev).
 */
public class ReportGenerateIT {

    public static final String ALLURE_RESULTS = "allure-results/sample-testsuite.xml";

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    public static String commandline;

    public static String jdk;

    @BeforeClass
    public static void setUp() throws Exception {
        jdk = getJdk();
        commandline = getAllureCommandline();
    }

    @Test
    public void shouldGenerateReport() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList("allure-results")));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
    }

    @Test
    public void shouldGenerateReportForMatrixItem() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "a", "b"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList("allure-results")));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getRuns(), hasSize(2));
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureReportBuildBadgeAction.class), hasSize(1));
        }
    }

    @Test
    public void shouldGenerateAggregatedReportForMatrixJobs() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "first", "second", "third"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList("allure-results")));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
    }

    @Test
    public void shouldGenerateReportOnSlave() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        Label label = new LabelAtom(UUID.randomUUID().toString());
        jRule.createOnlineSlave(label);

        project.setAssignedLabel(label);
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList("allure-results")));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
    }

    @Test
    public void shouldFailBuildIfNoResultsFound() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList("allure-results")));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        jRule.assertBuildStatus(Result.FAILURE, build);
    }

    protected SCM getSimpleFileScm(String resourceName, String path) throws IOException {
        //noinspection ConstantConditions
        return new SingleFileSCM(path, getClass().getClassLoader().getResource(resourceName));
    }

    protected AllureReportPublisher createAllurePublisher(List<String> resultsPaths) throws Exception {
        return new AllureReportPublisher(new AllureReportConfig(
                jdk, commandline, Collections.emptyList(), ReportBuildPolicy.ALWAYS, false, resultsPaths
        ));
    }

    protected static String getJdk() {
        return jRule.jenkins.getJDKs().get(0).getName();
    }

    protected static String getAllureCommandline() throws Exception {
        Path allureHome = folder.newFolder().toPath();
        FilePath allure = jRule.jenkins.getRootPath().createTempFile("allure", "zip");
        //noinspection ConstantConditions
        allure.copyFrom(ReportGenerateIT.class.getClassLoader().getResource("allure-commandline.zip"));
        allure.unzip(new FilePath(allureHome.toFile()));

        AllureInstallation installation = new AllureInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation.getName();
    }
}
