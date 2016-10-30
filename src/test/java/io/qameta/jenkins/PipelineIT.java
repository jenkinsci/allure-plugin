package io.qameta.jenkins;

import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import io.qameta.jenkins.testdata.ResourceWithJenkinsfileScm;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import static io.qameta.jenkins.testdata.TestUtils.assertHasReport;
import static io.qameta.jenkins.testdata.TestUtils.getAllureCommandline;
import static io.qameta.jenkins.testdata.TestUtils.getJdk;

/**
 * @author charlie (Dmitry Baev).
 */
public class PipelineIT {

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    public static String commandline;

    public static String jdk;

    @BeforeClass
    public static void setUp() throws Exception {
        jdk = getJdk(jRule);
        commandline = getAllureCommandline(jRule, folder);
    }

    @Test
    @Ignore("doesn't work properly yet")
    public void shouldSupportPipeline() throws Exception {
        WorkflowJob project = jRule.createProject(WorkflowJob.class);
        project.setDefinition(new CpsScmFlowDefinition(new ResourceWithJenkinsfileScm(
                "sample-testsuite.xml",
                "allure-results/sample-testsuite.xml",
                "sample-jenkinsFile",
                "jenkinsFile"
        ), "jenkinsFile"));
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("resultsPath", "allure-results")
        ));
        WorkflowRun build = jRule.buildAndAssertSuccess(project);
        System.out.println(JenkinsRule.getLog(build));
        assertHasReport(build);
    }
}
