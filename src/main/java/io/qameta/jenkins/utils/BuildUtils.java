package io.qameta.jenkins.utils;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Artem Eroshenko <erosenkoam@me.com>
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    @Nonnull
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> Optional<T> getBuildTool( //NOSONAR
            @Nullable T tool, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Optional<Node> node = Optional.ofNullable(Computer.currentComputer())
                .map(Computer::getNode);
        if (node.isPresent() && Objects.nonNull(tool)) {
            return Optional.of(tool.forNode(node.get(), listener).forEnvironment(env));
        }
        return Optional.empty();
    }

    public static EnvVars getBuildEnvVars(Run<?, ?> run, TaskListener listener) //NOSONAR
            throws IOException, InterruptedException {
        EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
        }
        return env;
    }

}