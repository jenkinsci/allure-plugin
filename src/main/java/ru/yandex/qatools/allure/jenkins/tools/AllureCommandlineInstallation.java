package ru.yandex.qatools.allure.jenkins.tools;

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
import ru.yandex.qatools.allure.jenkins.Messages;

import javax.annotation.Nonnull;
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

    @DataBoundConstructor
    public AllureCommandlineInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    @SuppressWarnings("TrailingComment")
    public String getExecutable(@Nonnull Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            @Override
            public String call() throws IOException {
                final Path executable = getExecutablePath();
                if (executable == null || Files.notExists(executable)) {
                    throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, executable));
                }
                return executable.toAbsolutePath().toString();
            }
        });
    }

    public String getMajorVersion(@Nonnull Launcher launcher) throws InterruptedException, IOException {
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            @Override
            public String call() throws IOException {
                final Path home = getHomePath();
                if (home == null || Files.notExists(home)) {
                    throw new IOException(String.format(CAN_FIND_ALLURE_MESSAGE, home));
                }
                return Files.exists(home.resolve("app/allure-bundle.jar")) ? "1" : "2";
            }
        });
    }

    private Path getHomePath() {
        final String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        if (home == null) {
            return null;
        }

        if (Files.exists(Paths.get(home).resolve("bin").resolve("allure"))) {
            return Paths.get(home);
        }

        final File[] listOfFiles = Paths.get(home).toFile().listFiles();
        if (listOfFiles == null || listOfFiles.length == 0) {
            return null;
        }

        File allureDir = null;
        for (File file : listOfFiles) {
            if (file.isDirectory() && file.getName().startsWith("allure")) {
                allureDir = file;
            }
        }

        return allureDir == null ? null : allureDir.toPath();
    }

    private Path getExecutablePath() {
        final Path home = getHomePath();
        return home == null ? null : home.resolve(Functions.isWindows() ? "bin/allure.bat" : "bin/allure");
    }

    @Override
    public AllureCommandlineInstallation forEnvironment(@Nonnull EnvVars environment) {
        return new AllureCommandlineInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public AllureCommandlineInstallation forNode(@Nonnull Node node, TaskListener log)
            throws IOException, InterruptedException {
        return new AllureCommandlineInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Override
    public void buildEnvVars(EnvVars env) {
        final Path home = this.getHomePath();
        if (home != null) {
            env.put("ALLURE_HOME", home.toAbsolutePath().toString());
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
        @Nonnull
        public String getDisplayName() {
            return Messages.AllureCommandlineInstallation_DisplayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new AllureCommandlineInstaller(null));
        }

        @Override
        public void setInstallations(AllureCommandlineInstallation... installations) {
            super.setInstallations(installations);
            save();
        }
    }

}
