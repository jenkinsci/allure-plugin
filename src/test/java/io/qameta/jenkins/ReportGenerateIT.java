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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * @author charlie (Dmitry Baev).
 */
@RunWith(Parameterized.class)
public class ReportGenerateIT {

    @Rule
    public JenkinsRule jRule = new JenkinsRule();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Parameterized.Parameter
    public String resultsFolderName;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[]{"results"},
                new Object[]{"with space/and even more"},
                new Object[]{"Program Files(x86)/Allure Report"}
        );
    }

    @Test
    public void shouldGenerateReport() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        project.setScm(getSimpleFileScm("sample-testsuite.xml", resultsFolderName + "/sample-testsuite.xml"));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList(resultsFolderName)));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
    }

    @Test
    public void shouldGenerateAggregatedReportForMatrixJobs() throws Exception {
        MatrixProject project = jRule.createProject(MatrixProject.class);
        project.getAxes().add(new Axis("labels", "first", "second", "third"));
        project.setScm(getSimpleFileScm("sample-testsuite.xml", resultsFolderName + "/sample-testsuite.xml"));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList(resultsFolderName)));

        MatrixBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));

        assertThat(build.getRuns(), hasSize(3));
        for (MatrixRun run : build.getRuns()) {
            jRule.assertBuildStatus(Result.SUCCESS, run);
            assertThat(run.getActions(AllureReportBuildBadgeAction.class), hasSize(1));
        }
    }

    @Test
    public void shouldGenerateReportOnSlave() throws Exception {
        FreeStyleProject project = jRule.createFreeStyleProject();

        Label label = new LabelAtom(UUID.randomUUID().toString());
        jRule.createOnlineSlave(label);

        project.setAssignedLabel(label);
        project.setScm(getSimpleFileScm("sample-testsuite.xml", resultsFolderName + "/sample-testsuite.xml"));
        project.getPublishersList().add(createAllurePublisher(Collections.singletonList(resultsFolderName)));

        FreeStyleBuild build = jRule.buildAndAssertSuccess(project);
        List<AllureReportBuildBadgeAction> actions = build.getActions(AllureReportBuildBadgeAction.class);
        assertThat(actions, hasSize(1));
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

        AllureInstallation installation = new AllureInstallation(
                "Default", allureHome.toAbsolutePath().toString(), JenkinsRule.NO_PROPERTIES);
        jRule.jenkins.getDescriptorByType(AllureInstallation.DescriptorImpl.class)
                .setInstallations(installation);
        return installation.getName();
    }

    protected AllureReportPublisher createAllurePublisher(List<String> resultsPaths) throws Exception {
        return new AllureReportPublisher(new AllureReportConfig(getJdk(), getAllureCommandline(),
                Collections.emptyList(), ReportBuildPolicy.ALWAYS, false, resultsPaths));
    }
}
