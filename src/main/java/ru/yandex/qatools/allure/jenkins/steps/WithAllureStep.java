package ru.yandex.qatools.allure.jenkins.steps;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import java.util.Set;

import static ru.yandex.qatools.allure.jenkins.utils.BuildUtils.getAllureInstallations;

/**
 * @author Egor Borisov ehborisov@gmail.com
 */
public class WithAllureStep extends Step {

    private String commandline;

    @DataBoundConstructor
    public WithAllureStep(String commandline) {
        this.commandline = commandline;
    }

    public String getCommandline() {
        return commandline;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AllureStepExecution(context, this);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "withAllure";
        }

        @Override
        public String getDisplayName() {
            return "Provide Allure commandline installation";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class, Launcher.class, EnvVars.class, Run.class);
        }

        @Restricted(NoExternalUse.class)
        public ListBoxModel doFillCommandlineItems() {
            ListBoxModel r = new ListBoxModel();
            for (AllureCommandlineInstallation installation : getAllureInstallations()) {
                r.add(installation.getName());
            }
            return r;
        }
    }
}
