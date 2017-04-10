package ru.yandex.qatools.allure.jenkins.steps;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;

import static ru.yandex.qatools.allure.jenkins.utils.BuildUtils.getAllureInstallations;

/**
 * @author Egor Borisov ehborisov@gmail.com
 */
public class AllureStepExecution extends StepExecution implements Serializable {

    private static final String WITH_ALLURE = "WITH_ALLURE";
    private final transient WithAllureStep step;
    private final transient TaskListener listener;
    private final transient FilePath ws;
    private final transient Launcher launcher;
    private final transient EnvVars env;
    private final transient Run<?, ?> build;

    private transient PrintStream console;
    private transient Computer computer;
    private transient AllureCommandlineInstallation installation;

    public AllureStepExecution(StepContext context, WithAllureStep step) throws Exception {
        super(context);
        this.step = step;
        listener = context.get(TaskListener.class);
        ws = context.get(FilePath.class);
        launcher = context.get(Launcher.class);
        env = context.get(EnvVars.class);
        build = context.get(Run.class);
    }

    @Override
    public boolean start() throws Exception {
        console = listener.getLogger();
        getComputer();
        setupAllure();

        try {
            getContext().newBodyInvoker().withContexts(env).start();
            getContext().onSuccess(null);
        } catch (Exception e) {
            getContext().onFailure(e);
        }

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) {
        console.print("[withAllure] Allure report generation failed with error " + cause.getMessage());
    }

    private void setupAllure() throws IOException, InterruptedException {
        Node node = getComputer().getNode();
        if (node == null) {
            throw new AbortException("[withAllure] Could not obtain the Node for the computer: "
                    + getComputer().getName());
        }

        String allureInstallationName = step.getCommandline();
        if (allureInstallationName == null) {
            throw new AbortException("[withAllure] Define an Allure Commandline installation to use");
        }

        AllureCommandlineInstallation installation = null;
        for (AllureCommandlineInstallation i : getAllureInstallations()) {
            if (allureInstallationName.equals(i.getName())) {
                installation = i;
                console.println("[withAllure] using Allure Commandline installation '" + installation.getName() + "'");
                break;
            }
        }
        if (installation == null) {
            throw new AbortException("[withAllure] Could not find Allure Commandline installation with name '"
                    + allureInstallationName + "'.");
        }

        this.installation = installation.forNode(node, listener);
        this.installation.buildEnvVars(env);
        env.put(WITH_ALLURE, allureInstallationName);
    }

    private
    @Nonnull
    Computer getComputer() throws AbortException {
        if (computer != null) {
            return computer;
        }

        String node = null;
        final Jenkins j = Jenkins.getInstance();
        for (Computer c : j.getComputers()) {
            if (c.getChannel() == launcher.getChannel()) {
                node = c.getName();
                break;
            }
        }

        if (node == null) {
            throw new AbortException("[withAllure] Could not find computer for the job");
        }

        computer = j.getComputer(node);
        if (computer == null) {
            throw new AbortException("[withAllure] No such computer " + node);
        }

        return computer;
    }
}
