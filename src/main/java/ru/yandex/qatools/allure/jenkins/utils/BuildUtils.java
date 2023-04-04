/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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
import java.util.Objects;

/**
 * @author Artem Eroshenko {@literal <erosenkoam@me.com>}
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    @SuppressWarnings({"ParameterAssignment", "PMD.AvoidReassigningParameters"})
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T setUpTool(
            @Nullable T tool,
            final @Nonnull Launcher launcher,
            final @Nonnull TaskListener listener,
            final @Nonnull EnvVars env)
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
            final T tool,
            final EnvVars envVars,
            final Launcher launcher) throws IOException, InterruptedException {
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

    public static Computer getComputer(final Launcher launcher) {

        for (Computer computer : Jenkins.get().getComputers()) {
            if (Objects.equals(computer.getChannel(), launcher.getChannel())) {
                return computer;
            }
        }
        return null;
    }

    @SuppressWarnings("TrailingComment")
    public static EnvVars getBuildEnvVars(final Run<?, ?> run,
                                          final TaskListener listener) //NOSONAR
            throws IOException, InterruptedException {
        final EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
        }
        return env;
    }

}
