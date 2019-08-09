package ru.yandex.qatools.allure.jenkins;

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
import ru.yandex.qatools.allure.jenkins.testdata.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.yandex.qatools.allure.jenkins.testdata.TestUtils.createAllurePublisher;
import static ru.yandex.qatools.allure.jenkins.testdata.TestUtils.getSimpleFileScm;

/**
 * eroshenkoam
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
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("items", "first", "second"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", ALLURE_RESULTS));
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, "allure-results"));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);

        assertThat(build.getActions(AllureReportBuildAction.class)).hasSize(1);
        assertThat(build.getRuns()).hasSize(2);
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureReportBuildAction.class)).hasSize(1);
        }
    }




}
