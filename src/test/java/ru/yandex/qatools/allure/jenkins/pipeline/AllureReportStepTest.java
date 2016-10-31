package ru.yandex.qatools.allure.jenkins.pipeline;

import hudson.FilePath;
import hudson.model.Result;
import hudson.tools.ToolProperty;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AllureReportStepTest {
    private static String REPORT_FILENAME = "report-test-generated";

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule rule = new JenkinsRule();

    @Test
    public void shouldGenerateAllureReport() throws Exception {
        WorkflowJob project = rule.jenkins.createProject(WorkflowJob.class, "example-pipeline-job-1");
        FilePath workspace = rule.jenkins.getWorkspaceFor(project);
        project.setDefinition(new CpsFlowDefinition("node { allureReport(paths: ['allure-reports'], commandLine: 'test') }", true));
        setupCommandLine(workspace, 0);

        WorkflowRun run = project.scheduleBuild2(0).get();

        rule.assertBuildStatusSuccess(run);
        assertCommandLineExecuted(workspace, 0);
    }

    @Test
    public void shouldFailExecutionIfItCannotExecuteCommandLineTool() throws Exception {
        WorkflowJob project = rule.jenkins.createProject(WorkflowJob.class, "example-pipeline-job-2");
        FilePath workspace = rule.jenkins.getWorkspaceFor(project);
        project.setDefinition(new CpsFlowDefinition("node { allureReport(paths: ['allure-reports'], commandLine: 'test') }", true));
        setupCommandLine(workspace, 1);

        WorkflowRun run = project.scheduleBuild2(0).get();

        rule.assertBuildStatus(Result.FAILURE, run);
        assertCommandLineExecuted(workspace, 1);
    }

    private void assertCommandLineExecuted(FilePath workspace, int code) throws Exception {
        FilePath reportFile = workspace.child(REPORT_FILENAME);
        assertTrue("Script executed properly", reportFile.exists());
        assertEquals(Integer.toString(code), reportFile.readToString().trim());
    }

    private void setupCommandLine(FilePath workspace, int exitCode) throws IOException, InterruptedException {
        rule.jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class).setInstallations(
                new AllureCommandlineInstallation("test", workspace.getRemote(), Collections.<ToolProperty<?>>emptyList())
        );
        FilePath allureScript = workspace.child(AllureCommandlineInstallation.BIN_PATH + "/allure");
        allureScript.delete();
        allureScript.write("#!/bin/sh\necho " + exitCode + " >> " + REPORT_FILENAME + "\nexit " + exitCode + "\n", "UTF-8");
        setExecutable(allureScript);
    }

    private void setExecutable(FilePath file) {
        new File(file.getRemote()).setExecutable(true);
    }
}
