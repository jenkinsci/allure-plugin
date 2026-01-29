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
package org.allurereport.jenkins.tools;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.allurereport.jenkins.Messages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Tool installation for Allure 3 which expects the allure command to be available in PATH.
 * Allure 3 is npm-based and should be installed via: npm install -g allure
 */
public class Allure3Installation extends ToolInstallation
        implements EnvironmentSpecific<Allure3Installation>, NodeSpecific<Allure3Installation>,
        AllureInstallation {

    private static final String ALLURE = "allure";
    private static final String CAN_FIND_ALLURE_MESSAGE = "Can't find allure command in PATH";

    @DataBoundConstructor
    public Allure3Installation(final String name,
                               final String home,
                               final List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    /**
     * Get the executable path for the allure command.
     * For Allure 3, we expect 'allure' to be in PATH.
     */
    @SuppressWarnings("TrailingComment")
    public String getExecutable(final @NonNull Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new GetExecutable());
    }

    /**
     * Get the major version of Allure.
     * For Allure 3 installations, this always returns "3".
     */
    public String getMajorVersion(final @NonNull Launcher launcher) throws InterruptedException, IOException {
        return launcher.getChannel().call(new GetMajorVersion());
    }

    @Override
    public Allure3Installation forEnvironment(final @NonNull EnvVars environment) {
        return new Allure3Installation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public Allure3Installation forNode(final @NonNull Node node,
                                       final TaskListener log)
            throws IOException, InterruptedException {
        return new Allure3Installation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Override
    public void buildEnvVars(final EnvVars env) {
        // No special environment variables needed for Allure 3
    }

    /**
     * Callable to get the executable path on the remote node.
     */
    private static final class GetExecutable extends MasterToSlaveCallable<String, IOException> {
        @Override
        public String call() throws IOException {
            final String executable = Functions.isWindows() ? "allure.cmd" : ALLURE;

            // Try to find allure in PATH by checking if it's executable
            try {
                final ProcessBuilder pb = new ProcessBuilder(executable, "--version");
                pb.redirectErrorStream(true);
                final Process process = pb.start();
                final int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return executable;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(CAN_FIND_ALLURE_MESSAGE, e);
            } catch (IOException e) {
                throw new IOException(CAN_FIND_ALLURE_MESSAGE, e);
            }
            throw new IOException(CAN_FIND_ALLURE_MESSAGE);
        }
    }

    /**
     * Callable to get the major version on the remote node.
     */
    private static final class GetMajorVersion extends MasterToSlaveCallable<String, IOException> {
        @Override
        public String call() throws IOException {
            final String executable = Functions.isWindows() ? "allure.cmd" : ALLURE;

            try {
                final ProcessBuilder pb = new ProcessBuilder(executable, "--version");
                pb.redirectErrorStream(true);
                final Process process = pb.start();

                String version = null;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    final String line = reader.readLine();
                    if (line != null) {
                        version = line.trim();
                    }
                }

                final int exitCode = process.waitFor();
                if (exitCode == 0 && version != null) {
                    // Extract major version from version string (e.g., "3.1.0" -> "3")
                    if (version.startsWith("3")) {
                        return "3";
                    } else if (version.startsWith("2")) {
                        return "2";
                    } else if (version.startsWith("1")) {
                        return "1";
                    }
                    // Default to returning the first character as major version
                    return version.substring(0, 1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to get Allure version", e);
            }
            // Default to version 3 for Allure3Installation
            return "3";
        }
    }

    /**
     * Descriptor for Allure 3 installation.
     */
    @Extension
    @Symbol("allure3")
    public static class DescriptorImpl extends ToolDescriptor<Allure3Installation> {

        public DescriptorImpl() {
            load();
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.Allure3Installation_DisplayName();
        }

        @Override
        public List<Allure3Installation> getInstallations() {
            return super.getInstallations().isEmpty()
                    ? Collections.singletonList(new Allure3Installation("Allure 3", "", Collections.emptyList()))
                    : super.getInstallations();
        }

        @Override
        public void setInstallations(final Allure3Installation... installations) {
            super.setInstallations(installations);
            save();
        }

        /**
         * Validates that allure is available in PATH.
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckHome(@QueryParameter final String value) {
            // For Allure 3, home can be empty as we use PATH
            return FormValidation.ok();
        }

        /**
         * Validates that allure command is available.
         */
        @SuppressWarnings("unused")
        public FormValidation doValidate() {
            final String executable = Functions.isWindows() ? "allure.cmd" : ALLURE;
            try {
                final ProcessBuilder pb = new ProcessBuilder(executable, "--version");
                pb.redirectErrorStream(true);
                final Process process = pb.start();

                String version = null;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    version = reader.readLine();
                }

                final int exitCode = process.waitFor();
                if (exitCode == 0 && version != null) {
                    return FormValidation.ok("Allure version: " + version.trim());
                }
                return FormValidation.error("Allure command not found or returned error");
            } catch (IOException | InterruptedException e) {
                return FormValidation.error("Cannot find allure in PATH. "
                        + "Please install Allure 3 via: npm install -g allure");
            }
        }
    }
}
