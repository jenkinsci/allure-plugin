package io.qameta.jenkins;

import hudson.FilePath;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;

import static io.qameta.jenkins.testdata.TestUtils.assertHasReport;
import static io.qameta.jenkins.testdata.TestUtils.getAllureCommandline;
import static io.qameta.jenkins.testdata.TestUtils.getJdk;

/**
 * @author charlie (Dmitry Baev).
 */
public class PipelineIT {

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
    public void shouldSupportPipeline() throws Exception {
        WorkflowJob project = jRule.createProject(WorkflowJob.class);
        prepareWorkspace(project);

        FlowDefinition definition = new CpsFlowDefinition("node { allure(resultsPaths: [paths]) }", true);
        project.setDefinition(definition);
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("paths", "allure-results")
        ));
        WorkflowRun build = jRule.buildAndAssertSuccess(project);
        assertHasReport(build);
    }

    private void prepareWorkspace(WorkflowJob project) throws IOException, InterruptedException {
        FilePath workspace = jRule.jenkins.getWorkspaceFor(project);
        String testSuiteFileName = "sample-testsuite.xml";
        FilePath allureReportsDir = workspace.child("allure-results").child(testSuiteFileName);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(testSuiteFileName)) {
            allureReportsDir.copyFrom(is);
        }
    }
}
