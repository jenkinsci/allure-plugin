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
package org.allurereport.jenkins.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * @author Artem Eroshenko {@literal <erosenkoam@me.com>}
 */
public final class BuildUtils {

    private BuildUtils() {
    }

    @SuppressWarnings({"ParameterAssignment", "PMD.AvoidReassigningParameters"})
    public static <T extends ToolInstallation & EnvironmentSpecific<T> & NodeSpecific<T>> T setUpTool(@Nullable T tool,
        final @NonNull Launcher launcher, final @NonNull TaskListener listener,
            final @NonNull EnvVars env)
            throws IOException, InterruptedException {

        if (tool == null) {
            return null;
        }
        final Computer computer = getComputer(launcher);
        if (computer != null && computer.getNode() != null) {
            tool = tool.forNode(computer.getNode(), listener);
        }
        tool = tool.forEnvironment(env);
        tool.buildEnvVars(env);
        return tool;
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
