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
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.allurereport.jenkins.Messages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * @author Artem Eroshenko {@literal <eroshenkoam@yandex-team.ru>}
 */
public class AllureCommandlineInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureCommandlineInstallation>, NodeSpecific<AllureCommandlineInstallation> {

    private static final String CAN_FIND_ALLURE_MESSAGE = "Can't find allure commandline <%s>";
    private static final String ALLURE = "allure";

    @DataBoundConstructor
    public AllureCommandlineInstallation(final String name,
                                         final String home,
                                         final List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    @SuppressWarnings("TrailingComment")
    public String getExecutable(final @NonNull Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new GetExecutable(getHome()));
    }

    public String getMajorVersion(final @NonNull Launcher launcher) throws InterruptedException, IOException {
        return launcher.getChannel().call(new GetMajorVersion(getHome()));
    }

    private static Path getHomePath(final String rawHome) {

        final String home = Util.replaceMacro(rawHome, EnvVars.masterEnvVars);
        if (home == null) {
            return null;
        }

        final Path homePath = Paths.get(home);
        if (Files.exists(homePath.resolve("bin").resolve(ALLURE))) {
            return homePath;
        }

        final File[] listOfFiles = homePath.toFile().listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            return null;
        }

        File allureDir = null;
        for (File file : listOfFiles) {
            if (file.isDirectory() && file.getName().startsWith(ALLURE)) {
                allureDir = file;
            }
        }

        return allureDir == null ? null : allureDir.toPath();
    }

    private static Path getExecutablePath(final String rawHome) {
        final Path home = getHomePath(rawHome);
        return home == null ? null : home.resolve(Functions.isWindows() ? "bin/allure.bat" : "bin/allure");
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
        final Path home = getHomePath(getHome());
        if (home != null) {
            env.put("ALLURE_HOME", home.toAbsolutePath().toString());
        }
    }

    private static final class GetMajorVersion extends MasterToSlaveCallable<String, IOException> {
        private final String rawHome;
        GetMajorVersion(final String rawHome) {
            this.rawHome = rawHome;
        }
        @Override
        public String call() throws IOException {
            final Path home = getHomePath(rawHome);
            if (home == null || Files.notExists(home)) {
                throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, home));
            }
            return Files.exists(home.resolve("app/allure-bundle.jar")) ? "1" : "2";
        }
    }

    private static final class GetExecutable extends MasterToSlaveCallable<String, IOException> {
        private final String rawHome;
        GetExecutable(final String rawHome) {
            this.rawHome = rawHome;
        }
        @Override
        public String call() throws IOException {
            final Path executable = getExecutablePath(rawHome);
            if (executable == null || Files.notExists(executable)) {
                throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, executable));
            }
            return executable.toAbsolutePath().toString();
        }
    }

    /**
     * Allure tool descriptor class that defines displayed text for allure cli installation.
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
            return Collections.singletonList(new AllureCommandlineInstaller(null));
        }

        @Override
        public void setInstallations(final AllureCommandlineInstallation... installations) {
            super.setInstallations(installations);
            save();
        }
    }

}
