package ru.yandex.qatools.allure.jenkins.utils;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author Artem Eroshenko <erosenkoam@me.com>
 */
public class BuildUtils {

    public BuildUtils() {
    }

    @Nullable
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T getBuildTool(
            @Nullable T tool, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Node node = Computer.currentComputer().getNode();
        if (tool == null || node == null) {
            return null;
        }
        T result = tool.forNode(node, listener);
        result = result.forEnvironment(env);
        return result;
    }

    public static EnvVars getBuildEnvVars(Run<?, ?> run, TaskListener listener)
            throws IOException, InterruptedException {
        EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
        }
        return env;
    }

}