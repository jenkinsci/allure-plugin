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
import hudson.Proc;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import jenkins.security.MasterToSlaveCallable;
import org.allurereport.jenkins.Messages;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ClassDataAbstractionCoupling")
public class AllureCommandlineInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureCommandlineInstallation>, NodeSpecific<AllureCommandlineInstallation>,
        AllureInstallation {

    private static final String CAN_FIND_ALLURE_MESSAGE = "Can't find allure commandline <%s>";
    private static final String VERSION_FLAG = "--version";
    private static final int VERSION_TIMEOUT_SECONDS = 30;

    @DataBoundConstructor
    public AllureCommandlineInstallation(final String name,
                                         final String home,
                                         final List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    @Override
    @SuppressWarnings("TrailingComment")
    public String getExecutable(final @NonNull Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        final AllureManagedInstaller managedInstaller = getManagedInstaller();
        if (managedInstaller != null) {
            final String version = managedInstaller.resolveVersion();
            if (AllureRuntimeManifest.isAllure3(version)) {
                final String relativePath = AllureManagedInstaller.executableRelativePath(
                        version,
                        launcher.getChannel().call(new IsWindows())
                );
                return launcher.getChannel().call(new GetExecutable(getHome(), relativePath));
            }
        }
        return launcher.getChannel().call(new GetExecutable(getHome(), null));
    }

    @Override
    public List<String> getCommandPrefix(final @NonNull Launcher launcher)
            throws InterruptedException, IOException {
        final AllureManagedInstaller managedInstaller = getManagedInstaller();
        if (managedInstaller != null) {
            final String version = managedInstaller.resolveVersion();
            if (AllureRuntimeManifest.isAllure3(version)) {
                return launcher.getChannel().call(new GetManagedCommand(getHome(), version));
            }
        }
        return AllureInstallation.super.getCommandPrefix(launcher);
    }

    @Override
    public String getVersion(final @NonNull Launcher launcher) throws InterruptedException, IOException {
        final String configuredVersion = getConfiguredVersion();
        if (configuredVersion != null) {
            return configuredVersion;
        }

        final String metadataVersion = launcher.getChannel().call(new GetMetadataVersion(getHome()));
        if (metadataVersion != null) {
            return metadataVersion;
        }

        final String executable = getExecutable(launcher);
        ArgumentListBuilder command = new ArgumentListBuilder();
        command.add(executable, VERSION_FLAG);
        if (!launcher.isUnix()) {
            command = command.toWindowsCommand();
        }

        final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        final Proc process = launcher.launch()
                .cmds(command)
                .stdout(standardOutput)
                .stderr(errorOutput)
                .quiet(true)
                .start();
        final int exitCode = process.joinWithTimeout(
                VERSION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
                TaskListener.NULL
        );
        if (exitCode == 0) {
            final String detected = firstNonBlankLine(standardOutput, errorOutput);
            if (detected != null) {
                return detected;
            }
        }

        final String legacyMajor = launcher.getChannel().call(new GetLegacyMajorVersion(getHome()));
        if (legacyMajor != null) {
            return legacyMajor;
        }
        throw new IOException("Cannot determine Allure version using " + executable);
    }

    @Override
    public String getMajorVersion(final @NonNull Launcher launcher) throws InterruptedException, IOException {
        final String version = getVersion(launcher);
        final int major = AllureRuntimeManifest.majorVersion(version);
        if (major == AllureRuntimeManifest.UNKNOWN_MAJOR_VERSION) {
            throw new IOException("Cannot determine Allure major version from " + version);
        }
        return Integer.toString(major);
    }

    private static String firstNonBlankLine(final ByteArrayOutputStream... outputs) {
        for (ByteArrayOutputStream output : outputs) {
            final String text = output.toString(StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
        }
        return null;
    }

    @Override
    public AllureCommandlineInstallation forEnvironment(final @NonNull EnvVars environment) {
        return new AllureCommandlineInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public AllureCommandlineInstallation forNode(final @NonNull Node node,
                                                 final TaskListener log)
            throws IOException, InterruptedException {
        return new AllureCommandlineInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Override
    public void buildEnvVars(final EnvVars env) {
        String home = getHome();
        final AllureManagedInstaller managedInstaller = getManagedInstaller();
        if (home != null && managedInstaller != null) {
            final String version = managedInstaller.getResolvedVersion();
            if (AllureRuntimeManifest.isAllure3(version)) {
                home += "/releases/" + AllureRuntimeManifest.releaseId(version);
            }
        }
        if (home != null) {
            env.put("ALLURE_HOME", home);
        }
    }

    private String getConfiguredVersion() throws IOException {
        final InstallSourceProperty installSource = getProperties().get(InstallSourceProperty.class);
        if (installSource == null || installSource.installers == null) {
            return null;
        }
        for (ToolInstaller installer : installSource.installers) {
            if (installer instanceof AllureManagedInstaller) {
                return ((AllureManagedInstaller) installer).resolveVersion();
            }
            if (installer instanceof AllureCommandlineDirectInstaller) {
                return ((AllureCommandlineDirectInstaller) installer).getVersion();
            }
            if (installer instanceof AllureCommandlineInstaller) {
                return ((AllureCommandlineInstaller) installer).id;
            }
        }
        return null;
    }

    private AllureManagedInstaller getManagedInstaller() {
        final InstallSourceProperty installSource = getProperties().get(InstallSourceProperty.class);
        if (installSource == null || installSource.installers == null) {
            return null;
        }
        for (ToolInstaller installer : installSource.installers) {
            if (installer instanceof AllureManagedInstaller) {
                return (AllureManagedInstaller) installer;
            }
        }
        return null;
    }

    private static final class GetMetadataVersion extends MasterToSlaveCallable<String, IOException> {
        private final String rawHome;

        GetMetadataVersion(final String rawHome) {
            this.rawHome = rawHome;
        }

        @Override
        public String call() throws IOException {
            return AllureLocalInstallationResolver.metadataVersion(rawHome);
        }
    }

    private static final class GetLegacyMajorVersion extends MasterToSlaveCallable<String, IOException> {
        private final String rawHome;

        GetLegacyMajorVersion(final String rawHome) {
            this.rawHome = rawHome;
        }

        @Override
        public String call() {
            return AllureLocalInstallationResolver.legacyMajorVersion(rawHome);
        }
    }

    private static final class GetExecutable extends MasterToSlaveCallable<String, IOException> {
        private final String rawHome;
        private final String relativePath;
        GetExecutable(final String rawHome, final String relativePath) {
            this.rawHome = rawHome;
            this.relativePath = relativePath;
        }
        @Override
        public String call() throws IOException {
            final Path executable;
            if (relativePath == null) {
                executable = AllureLocalInstallationResolver.executable(rawHome);
            } else {
                executable = rawHome == null ? null : Paths.get(rawHome).resolve(relativePath);
            }
            if (executable == null || Files.notExists(executable)) {
                throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, executable));
            }
            return executable.toAbsolutePath().toString();
        }
    }

    private static final class GetManagedCommand extends MasterToSlaveCallable<List<String>, IOException> {
        private final String rawHome;
        private final String version;

        GetManagedCommand(final String rawHome, final String version) {
            this.rawHome = rawHome;
            this.version = version;
        }

        @Override
        public List<String> call() throws IOException {
            if (rawHome == null) {
                throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, rawHome));
            }
            final List<String> command = new ArrayList<>();
            for (String relativePath : AllureManagedInstaller.commandRelativePaths(
                    version,
                    NodePlatform.detect()
            )) {
                final Path executablePart = Paths.get(rawHome).resolve(relativePath).toAbsolutePath();
                if (Files.notExists(executablePart)) {
                    throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, executablePart));
                }
                command.add(executablePart.toString());
            }
            return command;
        }
    }

    private static final class IsWindows extends MasterToSlaveCallable<Boolean, IOException> {
        @Override
        public Boolean call() {
            return Functions.isWindows();
        }
    }

    /**
     * Allure tool descriptor class that defines the displayed installation name.
     */
    @Extension
    @Symbol("allure")
    public static class DescriptorImpl extends ToolDescriptor<AllureCommandlineInstallation> {

        public DescriptorImpl() {
            load();
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.AllureCommandlineInstallation_DisplayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(
                    new AllureManagedInstaller(AllureManagedInstaller.VERSION_POLICY_RECOMMENDED)
            );
        }

        @Override
        public void setInstallations(final AllureCommandlineInstallation... installations) {
            super.setInstallations(installations);
            save();
        }
    }

}
