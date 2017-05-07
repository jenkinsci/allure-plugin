package ru.yandex.qatools.allure.jenkins.utils;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author Artem Eroshenko {@literal <erosenkoam@me.com>}
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    @SuppressWarnings("ParameterAssignment")
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T setUpTool(
            @Nullable T tool, @Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull EnvVars env)
            throws IOException, InterruptedException {

        if (tool == null) {
            return null;
        }

        final Computer computer = getComputer(launcher);
        if (computer != null && computer.getNode() != null) {
            tool = tool.forNode(computer.getNode(), listener).forEnvironment(env);
        }

        tool.buildEnvVars(env);
        return tool;
    }

    public static Computer getComputer(Launcher launcher) {
        for (Computer computer : Jenkins.getInstance().getComputers()) {
            if (computer.getChannel() == launcher.getChannel()) {
                return computer;
            }
        }
        return null;
    }

    @SuppressWarnings("TrailingComment")
    public static EnvVars getBuildEnvVars(Run<?, ?> run, TaskListener listener) //NOSONAR
            throws IOException, InterruptedException {
        final EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
        }
        return env;
    }

}
