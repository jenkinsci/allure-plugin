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
import jenkins.security.MasterToSlaveCallable;

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

        tool.buildEnvVars(env);
        final Computer computer = getComputer(launcher);
        if (computer != null && computer.getNode() != null) {
            tool = tool.forNode(computer.getNode(), listener).forEnvironment(env);
            setEnvVarsForNode(tool, env, launcher);
        }

        return tool;
    }

    private static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> void setEnvVarsForNode(
            final T tool, final EnvVars envVars, final Launcher launcher) throws IOException, InterruptedException {
        launcher.getChannel().call(new MasterToSlaveCallable<Void, RuntimeException>() {
            @Override
            public Void call() {
                if (tool != null) {
                    tool.buildEnvVars(envVars);
                }
                return null;
            }
        });
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
