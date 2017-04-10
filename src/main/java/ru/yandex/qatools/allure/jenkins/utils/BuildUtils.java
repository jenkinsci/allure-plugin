package ru.yandex.qatools.allure.jenkins.utils;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import jenkins.model.Jenkins;
import org.apache.commons.lang.reflect.FieldUtils;
import ru.yandex.qatools.allure.jenkins.tools.AllureCommandlineInstallation;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author Artem Eroshenko <erosenkoam@me.com>
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T getBuildTool(    //NOSONAR
                                                                                                             @Nullable T tool, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        if (tool == null) {
            return null;
        }

        if (tool.getHome() == null) {
            tool = getNestedToolInstallation(tool);
        }

        Computer computer = Computer.currentComputer();
        if (computer != null && computer.getNode() != null) {
            return tool.forNode(computer.getNode(), listener).forEnvironment(env);
        }
        tool.buildEnvVars(env);
        return tool;
    }

    public static EnvVars getBuildEnvVars(Run<?, ?> run, TaskListener listener) //NOSONAR
            throws IOException, InterruptedException {
        EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
        }
        return env;
    }

    @SuppressWarnings("unchecked")
    private static <T extends ToolInstallation> T getNestedToolInstallation(T tool) {
        try {
            ToolProperty<?> property = tool.getProperties().get(0);
            return (T) FieldUtils.readField(property, "tool", true);
        } catch (Exception e) {
            return null;
        }
    }

    public static AllureCommandlineInstallation[] getAllureInstallations() {
        return Jenkins.getInstance().getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                .getInstallations();
    }
}