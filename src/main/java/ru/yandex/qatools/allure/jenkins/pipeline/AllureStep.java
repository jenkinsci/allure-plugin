package ru.yandex.qatools.allure.jenkins.pipeline;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import ru.yandex.qatools.allure.jenkins.AllureReportPublisher;
import ru.yandex.qatools.allure.jenkins.config.AllureReportConfig;


/**
 * @author  Marat Mavlutov <mavlyutov@yandex-team.ru>
 */
public class AllureStep extends AbstractStepImpl {

    private String path;

    @DataBoundConstructor
    public AllureStep(String path) {
        this.setPath(path);
    }

    @DataBoundSetter
    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @Inject private transient AllureStep step;
        @StepContextParameter private transient Run<?,?> build;
        @StepContextParameter private transient FilePath workspace;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {

            AllureReportPublisher allureReportPublisher = new AllureReportPublisher(AllureReportConfig.newInstance(step.getPath()));
            allureReportPublisher.perform(build, workspace, launcher, listener);

            return null;
        }
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "allure";
        }

        @Override public String getDisplayName() {
            return "Build Allure Report";
        }
    }

}
