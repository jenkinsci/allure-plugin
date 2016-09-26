package ru.yandex.qatools.allure.jenkins.utils;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.*;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;
import jenkins.security.MasterToSlaveCallable;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Artem Eroshenko <erosenkoam@me.com>
 */
public class BuildUtils {

    public BuildUtils() {
    }

    public static File getBuildFile(final String path, Launcher launcher) throws IOException, InterruptedException {
        return getBuildFile(Paths.get(path), launcher);
    }

    public static File getBuildFile(final Path path, Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new MasterToSlaveCallable<File, IOException>() {
            @Override
            public File call() throws IOException {
                if (path == null || Files.notExists(path)) {
                    throw new FileNotFoundException(String.format("Can not find file by path '%s'", path));
                }
                return path.toFile();
            }
        });
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