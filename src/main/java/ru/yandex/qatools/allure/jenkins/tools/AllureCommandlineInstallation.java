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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureCommandlineInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureCommandlineInstallation>, NodeSpecific<AllureCommandlineInstallation> {

    @DataBoundConstructor
    public AllureCommandlineInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    public String getExecutable(@Nonnull Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            @Override
            public String call() throws IOException {
                Path executable = getExecutablePath();
                if (executable == null || Files.notExists(executable)) {
                    throw new IOException(String.format("Can't find allure commandline <%s>", executable));
                }
                return executable.toAbsolutePath().toString();
            }
        });
    }

    private Path getHomePath() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        return home == null ? null : Paths.get(home);
    }

    private Path getExecutablePath() {
        Path home = getHomePath();
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
        Path home = this.getHomePath();
        if (home != null) {
            env.put("ALLURE_HOME", home.toAbsolutePath().toString());
        }
    }

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