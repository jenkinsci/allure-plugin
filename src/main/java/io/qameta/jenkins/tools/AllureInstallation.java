package io.qameta.jenkins.tools;

import hudson.EnvVars;
import hudson.Extension;
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
import io.qameta.jenkins.Messages;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Artem Eroshenko <eroshenkoam@yandex-team.ru>
 */
public class AllureInstallation extends ToolInstallation
        implements EnvironmentSpecific<AllureInstallation>, NodeSpecific<AllureInstallation> {

    @DataBoundConstructor
    public AllureInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    public String getExecutable(Launcher launcher) throws InterruptedException, IOException { //NOSONAR
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            @Override
            public String call() throws IOException {
                return getExecutablePath()
                        .map(Path::toAbsolutePath)
                        .map(Path::toString)
                        .orElse("allure");
            }
        });
    }

    private Optional<Path> getHomePath() {
        String home = Util.replaceMacro(getHome(), EnvVars.masterEnvVars);
        return Optional.ofNullable(home).map(Paths::get);
    }

    public Optional<Path> getExecutablePath() {
        return getHomePath()
                .map(path -> path.resolve("bin/allure"))
                .filter(Files::isRegularFile);
    }

    @Override
    public AllureInstallation forEnvironment(@Nonnull EnvVars environment) {
        return new AllureInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public AllureInstallation forNode(@Nonnull Node node, TaskListener log)
            throws IOException, InterruptedException {
        return new AllureInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension
    @Symbol("allure")
    public static class DescriptorImpl extends ToolDescriptor<AllureInstallation> {

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
            return Collections.singletonList(new AllureInstaller(null));
        }

        @Override
        public void setInstallations(AllureInstallation... installations) {
            super.setInstallations(installations);
            save();
        }
    }
}