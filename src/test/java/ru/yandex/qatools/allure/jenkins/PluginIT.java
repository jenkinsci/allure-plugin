package ru.yandex.qatools.allure.jenkins;

import hudson.FilePath;
import hudson.matrix.Axis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SCM;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;
import ru.yandex.qatools.allure.jenkins.config.PropertyConfig;
import ru.yandex.qatools.allure.jenkins.config.ReportBuildPolicy;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * @author charlie (Dmitry Baev).
 */
public class PluginIT {

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldGenerateReport() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        project.setScm(getSimpleFileScm("sample-testsuite.xml", "results/sample-testsuite.xml"));
        project.getPublishersList().add(createAllurePublisher("results"));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureBuildAction> actions = build.getActions(AllureBuildAction.class);
        assertThat(actions, hasSize(1));
    }

    @Test
    public void shouldGenerateAggregatedReportForMatrixJobs() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "first", "second", "third"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", "results/sample-testsuite.xml"));
        project.getPublishersList().add(createAllurePublisher("results"));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureBuildAction> actions = build.getActions(AllureBuildAction.class);
        assertThat(actions, hasSize(1));

        assertThat(build.getRuns(), hasSize(3));
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureBuildAction.class), hasSize(1));
        }
    }

    protected SCM getSimpleFileScm(String resourceName, String path) throws IOException {
        //noinspection ConstantConditions
        return new SingleFileSCM(path, getClass().getClassLoader().getResource(resourceName));
    }

    protected String getJdk() {
        return jRule.jenkins.getJDKs().get(0).getName();
    }

    protected String getAllureCommandline() throws Exception {
        Path allureHome = folder.newFolder().toPath();
        FilePath allure = jRule.jenkins.getRootPath().createTempFile("allure", "zip");
        //noinspection ConstantConditions
        allure.copyFrom(getClass().getClassLoader().getResource("allure-commandline.zip"));
        allure.unzip(new FilePath(allureHome.toFile()));

        AllureCommandlineInstallation installation = new AllureCommandlineInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation.getName();
    }

    protected AllureReportPublisher createAllurePublisher(String resultsDirectories) throws Exception {
        return new AllureReportPublisher(new AllureReportConfig(getJdk(), getAllureCommandline(),
                resultsDirectories, Collections.<PropertyConfig>emptyList(), ReportBuildPolicy.ALWAYS, false)
        );
    }
}
