package ru.yandex.qatools.allure.jenkins.utils;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author Artem Eroshenko {@literal <erosenkoam@me.com>}
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    @SuppressWarnings({"TrailingComment", "ReturnCount"})
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T getBuildTool(//NOSONAR
            @Nullable T tool, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        if (tool == null) {
            return null;
        }
        final Computer computer = Computer.currentComputer();
        if (computer != null && computer.getNode() != null) {
            return tool.forNode(computer.getNode(), listener).forEnvironment(env);
        }
        tool.buildEnvVars(env);
        return tool;
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
